package com.uber.nullaway.autofix.fixer;

import com.google.errorprone.VisitorState;
import com.sun.tools.javac.code.Symbol;
import com.uber.nullaway.Config;
import com.uber.nullaway.ErrorMessage;
import com.uber.nullaway.NullAway;
import com.uber.nullaway.autofix.AutoFixConfig;
import com.uber.nullaway.autofix.out.Fix;

@SuppressWarnings("ALL")
public class Fixer {

  protected final AutoFixConfig config;

  public Fixer(Config config) {
    this.config = config.getAutoFixConfig();
  }

  public void fix(ErrorMessage errorMessage, Symbol symbol, VisitorState state) {
    Location location = new Location(symbol);
    if (!config.SUGGEST_ENABLED) return;
    // todo: remove this condition later, for now we are not supporting anonymous classes
    if (location.isInAnonymousClass()) return;
    Fix fix = buildFix(errorMessage, location);
    if (fix != null) {
      if (config.SUGGEST_DEEP) {
        fix.findEnclosing(state, errorMessage);
      }
      fix.index = NullAway.index;
      config.WRITER.saveFix(fix);
    }
  }

  protected Fix buildFix(ErrorMessage errorMessage, Location location) {
    Fix fix;
    switch (errorMessage.getMessageType()) {
      case RETURN_NULLABLE:
      case WRONG_OVERRIDE_RETURN:
      case WRONG_OVERRIDE_PARAM:
      case PASS_NULLABLE:
      case FIELD_NO_INIT:
      case ASSIGN_FIELD_NULLABLE:
        fix = new Fix();
        fix.location = location;
        fix.annotation = config.ANNOTATION_FACTORY.getNullable();
        fix.inject = true;
        break;
      default:
        return null;
    }
    if (fix != null) {
      fix.errorMessage = errorMessage;
    }
    return fix;
  }

  protected void suggestSuppressWarning(ErrorMessage errorMessage, Location location) {}
}
