package parsers.request;

import parsers.expression.ExpressionParser;
import parsers.expression.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestParser {

  private final Request req;

  public RequestParser(Request req){
    this.req = req;
  }

  public TokenizedRequest parse() throws MalformedRequestException {
    return switch (req.toString()) {
      case "BYE" -> new QuitRequest(req.toString(), List.of(req.toString()), TokenizedRequest.RequestType.QUIT);
      case "STAT_REQS" ->
              new StatRequest(req.toString(), List.of(req.toString()), TokenizedRequest.RequestType.STAT, StatRequest.StatKind.REQS);
      case "STAT_AVG_TIME" ->
              new StatRequest(req.toString(), List.of(req.toString()), TokenizedRequest.RequestType.STAT, StatRequest.StatKind.AVG_TIME);
      case "STAT_MAX_TIME" ->
              new StatRequest(req.toString(), List.of(req.toString()), TokenizedRequest.RequestType.STAT, StatRequest.StatKind.MAX_TIME);
      default -> parseComputationRequest();
    };
  }

  private TokenizedRequest parseComputationRequest() throws MalformedRequestException {
    int cursor = 0;
    final List<String> tokens = new ArrayList<>();
    final TokenizedRequest.RequestType requestType;
    final CompRequest.ComputationKind computationKind;
    final CompRequest.ValuesKind valuesKind;
    final List<CompRequest.VariableValue> variableValues = new ArrayList<>();
    final List<Node> expressions = new ArrayList<>();
    record Token(int start, int end) {
    }
    Matcher matcher;
    Token token;

    // parses the type of computation
    matcher = Pattern.compile("MAX|MIN|AVG|COUNT").matcher(req.toString());
    if (!matcher.find(cursor) || cursor != matcher.start()) {
      throw new MalformedRequestException("Invalid request type");
    } else {
      requestType = TokenizedRequest.RequestType.COMP;
      token = new Token(cursor, matcher.end());
      tokens.add(req.toString().substring(token.start, token.end));
      computationKind = switch (tokens.getLast()) {
        case "MAX" -> CompRequest.ComputationKind.MAX;
        case "MIN" -> CompRequest.ComputationKind.MIN;
        case "AVG" -> CompRequest.ComputationKind.AVG;
        case "COUNT" -> CompRequest.ComputationKind.COUNT;
        default -> null;
      };
      cursor = token.end;
    }


    matcher = Pattern.compile("_").matcher(req.toString());
    if (!matcher.find(cursor) || cursor != matcher.start()) {
      throw new MalformedRequestException("Missing underscore after computation type");
    } else {
      token = new Token(cursor, matcher.end());
      cursor = token.end;
    }

    // parses the kind of the values
    matcher = Pattern.compile("GRID|LIST").matcher(req.toString());
    if (!matcher.find(cursor) || cursor != matcher.start()) {
      throw new MalformedRequestException("Invalid ValuesKind parameter");
    } else {
      token = new Token(cursor, matcher.end());
      tokens.add(req.toString().substring(token.start, token.end));
      valuesKind = switch (tokens.getLast()) {
        case "GRID" -> CompRequest.ValuesKind.GRID;
        case "LIST" -> CompRequest.ValuesKind.LIST;
        default -> null;
      };
      cursor = token.end;
    }

    matcher = Pattern.compile(";").matcher(req.toString());
    if (!matcher.find(cursor) || cursor != matcher.start()) {
      throw new MalformedRequestException("Missing semicolon after ValuesKind");
    } else {
      token = new Token(cursor, matcher.end());
      cursor = token.end;
    }

    // parses the values functions
    do {

      matcher = Pattern.compile("[a-z][a-z0-9]:[^:]*:[^:]*:[^,;]*[,;]").matcher(req.toString());
      if (!matcher.find(cursor) || cursor != matcher.start()) {
        throw new MalformedRequestException("VariableValues syntax does not match VarName:JavaNum:JavaNum:JavaNum");
      } else {
        token = new Token(cursor, matcher.end()-1);
        tokens.add(req.toString().substring(token.start, token.end));
        variableValues.add(parseVariableValue(req.toString().substring(token.start, token.end)));
        cursor = token.end;
      }

    } while (req.toString().charAt(cursor++) == ',');

    cursor--; // the cursor skips the ; we need to bring it back by one position

    matcher = Pattern.compile(";").matcher(req.toString());
    if (!matcher.find(cursor) || cursor != matcher.start()) {
      throw new MalformedRequestException("Missing semicolon after last VariableValuesFunction");
    } else {
      token = new Token(cursor, matcher.end());
      cursor = token.end;
    }

    // parses the expressions
    do {
      //String expression = req.toString().substring(cursor);
      matcher = Pattern.compile(";").matcher(req.toString());
      try {
        if (!matcher.find(cursor)) {
          ExpressionParser exprParser = new ExpressionParser(req.toString().substring(cursor));
          expressions.add(exprParser.parse());
          cursor = req.toString().length()-1;
        } else {
          token = new Token(cursor, matcher.start());
          ExpressionParser exprParser = new ExpressionParser(req.toString().substring(token.start, token.end));
          expressions.add(exprParser.parse());
          cursor = token.end;
        }
      } catch (IllegalArgumentException e) {
        throw new MalformedRequestException("Invalid expression syntax: " + e.getMessage());
      }

    } while (req.toString().charAt(cursor++) == ';');

    return new CompRequest(req.toString(), tokens, requestType, computationKind, valuesKind, variableValues, expressions);

  }

  private static CompRequest.VariableValue parseVariableValue(String varVal) throws MalformedRequestException {
    CompRequest.VariableValue varValue;
    try {
      varValue = new CompRequest.VariableValue(varVal.split(":")[0],
              Double.parseDouble(varVal.split(":")[1]),
              Double.parseDouble(varVal.split(":")[2]),
              Double.parseDouble(varVal.split(":")[3])
      );
    } catch (IllegalArgumentException e) {
      throw new MalformedRequestException(e.getMessage());
    }

    return varValue;
  }


}
