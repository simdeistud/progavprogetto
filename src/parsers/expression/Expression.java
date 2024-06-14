package parsers.expression;

import java.util.*;
import java.util.function.Function;

public record Expression(Node root) {

  public Set<Variable> variables() {
    return extractVars(root);
  }

  private Set<Variable> extractVars(Node root) {
    Set<Variable> variables = new LinkedHashSet<>();
    if (root == null) return null;
    if (root.getClass().equals(Variable.class)) {
      variables.add((Variable) root);
    }
    for (Node child : root.children()) {
      variables.addAll(extractVars(child));
    }
    return variables;
  }

  public Function<Map<String, Double>, Double> toRealVariableVectorFunction() {
    return subtreeToRealVariableVectorFunction(root);
  }

  private Function<Map<String, Double>, Double> subtreeToRealVariableVectorFunction(Node root) {
    if (root instanceof Variable) {
      return m -> m.get(((Variable) root).name());
    }
    if (root instanceof Constant) {
      return m -> ((Constant) root).value();
    }
    return m ->
            ((Operator) root).type().toFunction().apply(
                    new double[]{
                            subtreeToRealVariableVectorFunction(root.children().getFirst()).apply(m),
                            subtreeToRealVariableVectorFunction(root.children().getLast()).apply(m)
                    });

  }

}

