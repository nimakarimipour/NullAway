package com.uber.nullaway.autofix;

import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.uber.nullaway.Config;
import com.uber.nullaway.ErrorMessage;
import com.uber.nullaway.Nullness;
import com.uber.nullaway.autofix.out.ErrorInfo;
import com.uber.nullaway.autofix.out.Fix;
import com.uber.nullaway.autofix.out.MethodInfo;
import com.uber.nullaway.autofix.out.SeperatedValueDisplay;
import com.uber.nullaway.autofix.out.TrackerNode;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;

public class Writer {
  public final String ERROR;
  public final String METHOD_INFO;
  public final String CALL_GRAPH;
  public final String SUGGEST_FIX;
  public final String FIELD_GRAPH;
  public final String DELIMITER = "$*$";

  public Writer(String outputDirectory) {
    this.ERROR = Paths.get(outputDirectory, "errors.csv").toString();
    this.METHOD_INFO = Paths.get(outputDirectory, "method_info.csv").toString();
    this.CALL_GRAPH = Paths.get(outputDirectory, "call_graph.csv").toString();
    this.SUGGEST_FIX = Paths.get(outputDirectory, "fixes.csv").toString();
    this.FIELD_GRAPH = Paths.get(outputDirectory, "field_graph.csv").toString();
  }

  public void saveFix(Fix fix) {
    appendToFile(fix, SUGGEST_FIX);
  }

  public void saveFieldGraphNode(Tree tree, VisitorState state) {
    TrackerNode node = new TrackerNode(ASTHelpers.getSymbol(tree), state.getPath());
    appendToFile(node, FIELD_GRAPH);
  }

  public void saveCallGraphNode(Tree tree, VisitorState state) {
    TrackerNode node = new TrackerNode(ASTHelpers.getSymbol(tree), state.getPath());
    appendToFile(node, CALL_GRAPH);
  }

  public void saveErrorNode(ErrorMessage errorMessage, VisitorState state, boolean deep) {
    ErrorInfo error = new ErrorInfo(errorMessage);
    if (deep) {
      error.findEnclosing(state);
    }
    appendToFile(error, ERROR);
  }

  public void saveMethodInfo(
      Symbol.MethodSymbol methodSymbol,
      Set<Element> nonnullFieldsAtExit,
      CompilationUnitTree c,
      VisitorState state,
      Config config) {
    String method = methodSymbol.toString();
    String clazz = ASTHelpers.enclosingClass(methodSymbol).toString();
    MethodInfo methodInfo = MethodInfo.findOrCreate(method, clazz);
    methodInfo.setUri(c);
    methodInfo.setNonnullFieldsElements(nonnullFieldsAtExit);
    methodInfo.setParent(methodSymbol, state);
    methodInfo.setParamNumber(methodSymbol.getParameters().size());
    List<Boolean> paramAnnotations = new ArrayList<>();
    for (Symbol.VarSymbol var : methodSymbol.getParameters()) {
      paramAnnotations.add(Nullness.hasNullableAnnotation(var, config));
    }
    methodInfo.setParamAnnotations(paramAnnotations);
    appendToFile(methodInfo, METHOD_INFO);
  }

  private void resetFile(String path, String header) {
    try {
      Files.deleteIfExists(Paths.get(path));
      OutputStream os = new FileOutputStream(path);
      header += "\n";
      os.write(header.getBytes(Charset.defaultCharset()), 0, header.length());
      os.flush();
      os.close();
    } catch (IOException e) {
      throw new RuntimeException(
          "Could not finish resetting File at Path: " + path + ", Exception: " + e);
    }
  }

  public void reset(AutoFixConfig config) {
    try {
      Files.createDirectories(Paths.get("/tmp/NullAwayFix/"));
      if (config.SUGGEST_ENABLED) {
        resetFile(SUGGEST_FIX, Fix.header(DELIMITER));
      }
      if (config.LOG_ERROR_ENABLED) {
        resetFile(ERROR, ErrorInfo.header(DELIMITER));
      }
      if (config.MAKE_METHOD_TREE_INHERITANCE_ENABLED) {
        resetFile(METHOD_INFO, MethodInfo.header(DELIMITER));
      }
      if (config.MAKE_CALL_GRAPH_ENABLED) {
        resetFile(CALL_GRAPH, TrackerNode.header(DELIMITER));
      }
      if (config.MAKE_FIELD_GRAPH_ENABLED) {
        resetFile(FIELD_GRAPH, TrackerNode.header(DELIMITER));
      }
    } catch (IOException e) {
      throw new RuntimeException("Could not finish resetting writer: " + e);
    }
  }

  private void appendToFile(SeperatedValueDisplay value, String filePath) {
    OutputStream os;
    String display = value.display(DELIMITER);
    if (display == null || display.equals("")) {
      return;
    }
    display = display.replaceAll("\\R+", " ").replaceAll("\t", "") + "\n";
    try {
      os = new FileOutputStream(filePath, true);
      os.write(display.getBytes(Charset.defaultCharset()), 0, display.length());
      os.flush();
      os.close();
    } catch (Exception e) {
      System.err.println("Error happened for writing at file: " + filePath);
    }
  }
}
