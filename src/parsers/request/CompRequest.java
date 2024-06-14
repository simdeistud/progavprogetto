package parsers.request;

import parsers.expression.Expression;
import parsers.expression.Node;

import java.util.List;
import java.util.function.Function;

public class CompRequest extends TokenizedRequest {

  private final ComputationKind compKind;
  private final ValuesKind valKind;
  private final List<VariableValue> variableValues;
  private final List<Expression> expressions;

  protected CompRequest(String req, List<String> tokens, RequestType type, ComputationKind compKind, ValuesKind valKind, List<VariableValue> variableValues, List<Node> expressions) {
    super(req, tokens, type);
    this.compKind = compKind;
    this.valKind = valKind;
    this.variableValues = variableValues;
    this.expressions = expressions.parallelStream().map(Expression::new).toList();
  }

  public ComputationKind kind() {
    return compKind;
  }

  public ValuesKind valuesKind() {
    return valKind;
  }

  public List<VariableValue> variableValues() {
    return variableValues;
  }

  public List<Expression> expressions() {
    return expressions;
  }

  public enum ComputationKind {
    MAX,
    MIN,
    AVG,
    COUNT
  }

  public enum ValuesKind {
    GRID,
    LIST
  }

  public record VariableValue(String name, Double startingVal, Double step, Double finalVal) {
    public VariableValue {
      if(step <= 0){
        throw new IllegalArgumentException("Step value for " + name + " must be >= 0");
      }
      if(finalVal < startingVal){
        throw new IllegalArgumentException("End of the range of " + name + " cannot be smaller than the start of the range");
      }
    }
  }
}
