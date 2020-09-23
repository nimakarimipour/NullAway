/*
 * Copyright (c) 2017 Uber Technologies, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.uber.nullaway.handlers;

import static com.google.errorprone.BugCheckerInfo.buildDescriptionFromChecker;

import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.uber.nullaway.ErrorMessage;
import com.uber.nullaway.NullAway;
import com.uber.nullaway.Nullness;
import com.uber.nullaway.dataflow.AccessPath;
import com.uber.nullaway.dataflow.NullnessStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;

public class RequiresNonnullHandler extends BaseNoOpHandler {

  private static final String annotName = "com.uber.nullaway.qual.RequiresNonnull";

  private @Nullable NullAway analysis;
  private @Nullable VisitorState state;

  @Override
  public void onMatchTopLevelClass(
      NullAway analysis, ClassTree tree, VisitorState state, Symbol.ClassSymbol classSymbol) {
    this.analysis = analysis;
    this.state = state;
  }

  @Override
  public void onMatchMethod(
      NullAway analysis, MethodTree tree, VisitorState state, Symbol.MethodSymbol methodSymbol) {

    Symbol.ClassSymbol classSymbol = ASTHelpers.enclosingClass(methodSymbol);
    ClassTree classTree = ASTHelpers.findClass(classSymbol, state);
    if (classTree == null) {
      reportMatch(tree, "Cannot find the enclosing class for method symbol: " + methodSymbol);
      super.onMatchMethod(analysis, tree, state, methodSymbol);
    }
    String contract = getContractFromAnnotation(methodSymbol);
    if (classTree != null && contract != null) {
      List<String> fields = new ArrayList<>();
      for (Tree t : classTree.getMembers()) {
        if (t.getKind().equals(Tree.Kind.VARIABLE)) {
          fields.add(((VariableTree) t).getName().toString());
          //          VariableTree vt = (VariableTree) t;
          //          analysis.setComputedNullness(vt.getInitializer(), Nullness.NONNULL);
        }
      }
      if (!fields.contains(contract)) {
        reportMatch(tree, "Class " + classSymbol + " does not have any field named: " + contract);
      }
    }
    super.onMatchMethod(analysis, tree, state, methodSymbol);
  }

  @Override
  public NullnessStore.Builder onDataflowInitialStore(
      UnderlyingAST underlyingAST,
      List<LocalVariableNode> parameters,
      NullnessStore.Builder result) {
    MethodTree methodTree = ((UnderlyingAST.CFGMethod) underlyingAST).getMethod();
    Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);
    String contract = getContractFromAnnotation(methodSymbol);
    if (contract == null || contract.equals("")) {
      return super.onDataflowInitialStore(underlyingAST, parameters, result);
    }
    ClassTree classTree = ((UnderlyingAST.CFGMethod) underlyingAST).getClassTree();
    for (Tree member : classTree.getMembers()) {
      if (member.getKind().equals(Tree.Kind.VARIABLE)) {
        VariableTree vt = ((VariableTree) member);
        if (vt.getName().toString().equals(contract)) {
          AccessPath accessPath = AccessPath.fromFieldAccess(ASTHelpers.getSymbol(vt));
          result.setInformation(accessPath, Nullness.NONNULL);
        }
      }
    }
    return result;
  }

  private void reportMatch(Tree errorLocTree, String message) {
    assert this.analysis != null && this.state != null;
    this.state.reportMatch(
        analysis
            .getErrorBuilder()
            .createErrorDescription(
                new ErrorMessage(ErrorMessage.MessageTypes.ANNOTATION_VALUE_INVALID, message),
                errorLocTree,
                buildDescriptionFromChecker(errorLocTree, analysis),
                this.state));
  }

  private static @Nullable String getContractFromAnnotation(Symbol.MethodSymbol sym) {
    for (AnnotationMirror annotation : sym.getAnnotationMirrors()) {
      Element element = annotation.getAnnotationType().asElement();
      assert element.getKind().equals(ElementKind.ANNOTATION_TYPE);
      if (((TypeElement) element).getQualifiedName().contentEquals(annotName)) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e :
            annotation.getElementValues().entrySet()) {
          if (e.getKey().getSimpleName().contentEquals("value")) {
            String value = e.getValue().toString();
            if (value.startsWith("\"") && value.endsWith("\"")) {
              value = value.substring(1, value.length() - 1);
            }
            return value;
          }
        }
      }
    }
    return null;
  }
}
