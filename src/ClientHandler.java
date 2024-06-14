import parsers.expression.Expression;
import parsers.expression.Variable;
import parsers.request.*;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

public class ClientHandler extends Thread {

  private final Socket socket;
  private final BufferedReader in;
  private final PrintWriter out;

  public ClientHandler(final Socket socket) throws IOException {
    this.socket = socket;
    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    out = new PrintWriter(socket.getOutputStream(), true);
  }

  public void run() {

    logInfo(socket.getInetAddress() + " has connected");

    do {

      Request candidateReq = null;

      try {
        candidateReq = new Request(in.readLine().trim());
      } catch (IOException e) {
        logError(e.getMessage());
        transmitErrorResponse(e.getMessage());
        break;
      } finally {
        logInfo((candidateReq == null ?
                "Didn't receive request" :
                "Received request \"" + candidateReq + "\"") +
                " from " +
                socket.getInetAddress()
        );
      }

      long requestStartTime = System.currentTimeMillis();

      TokenizedRequest request;

      try {
        request = new RequestParser(candidateReq).parse();
      } catch (MalformedRequestException e) {
        logError("Failed to parse request (" + e.getMessage() + ")");
        transmitErrorResponse(e.getMessage());
        continue;
      }

      if (request.type() == TokenizedRequest.RequestType.QUIT) {
        break;
      }

      String resp;
      try {
        resp = RequestHandler.generateResponse(request);
      } catch (Exception e) {
        logError(e.getMessage());
        transmitErrorResponse(e.getMessage());
        continue;
      }

      long responseTime = System.currentTimeMillis() - requestStartTime;
      Server.updateRespsStats(responseTime);

      transmitOkResponse(resp, responseTime);

      logInfo("Replied to " + socket.getInetAddress() + " with \"" + resp +  "\"");

    } while (true);

    try {
      in.close();
    } catch (IOException e) {
      logError(e.getMessage());
    } finally {
      out.close();
      logInfo(socket.getInetAddress() + " has disconnected");
    }

  }

  private void transmitErrorResponse(String msg) {
    transmitResponse("ERR;" + msg.toUpperCase());
  }

  private void transmitOkResponse(String msg, final long responseTimeInMillis) {
    transmitResponse("OK;" + String.format("%#.3f;%s", (double) responseTimeInMillis / (double) 1000, msg.toUpperCase()));
  }

  private void transmitResponse(String msg) {
    out.println(msg);
  }

  private void logInfo(String msg) {
    Server.logMessage("Info : " + msg, System.out);
  }

  private void logError(String msg) {
    Server.logMessage("Error : " + msg, System.err);
  }





  static class RequestHandler {

    static String generateResponse(TokenizedRequest request) throws ExecutionException, InterruptedException, MalformedRequestException {

      return switch (request.type()) {
        case QUIT -> generateQuitResponse();
        case STAT -> generateStatResponse((StatRequest) request);
        case COMP -> generateComputationResponse((CompRequest) request);
      };

    }

    private static String generateQuitResponse() {
      return "";
    }

    private static String generateStatResponse(final StatRequest req) throws ExecutionException, InterruptedException {

      Future<Long> result = Server.STAT_REQS_EXECUTOR.submit(() -> switch (req.kind()) {
        case StatRequest.StatKind.REQS -> Server.numOfOkResps();
        case StatRequest.StatKind.AVG_TIME -> Server.avgRespTimeInMillis();
        case StatRequest.StatKind.MAX_TIME -> Server.maxRespTimeInMillis();
      });

      return req.kind() == StatRequest.StatKind.REQS ?
              String.valueOf(result.get()) :
              String.format("%#.3f", (double) result.get() / (double) 1000);
    }

    private static String generateComputationResponse(final CompRequest req) throws MalformedRequestException, ExecutionException, InterruptedException {

      // we check that for every expression all the variables are present in the VariableValues declaration
      boolean variablesAreValid = switch (req.kind()) {
        // the COUNT operation is only concerned about the size of the domain, the expressions can be arbitrary
        case COUNT -> true;
        // the AVG operation only deals with the first expression inputted by the user, the rest can be garbage
        case AVG -> exprVarsSubsetOfVarVals(List.of(req.expressions().getFirst()), req.variableValues());
        default -> exprVarsSubsetOfVarVals(req.expressions(), req.variableValues());
      };

      if (!variablesAreValid) {
        throw new MalformedRequestException("Not all variables in the expressions are declared in the VariableValues");
      }

      return computeResult(req).toString();

    }

    private static Number computeResult(CompRequest req) throws MalformedRequestException, ExecutionException, InterruptedException {

      // Step 1 : Parsing of VariableValuesFunction to a
      // This function takes a VariableValue declaration and turns it into a set of real values
      // You should feed this function a null VariableValue in case it is not present in the declaration
      Function<CompRequest.VariableValue, Set<Double>> a = variableValue -> {
        Set<Double> result = new LinkedHashSet<>(); // we use a LinkedHashSet because the range has an inherent order, and we save on having to use a comparator after
        if (variableValue == null) {
          result = null;
        } else {
          for (double x = variableValue.startingVal(); x <= variableValue.finalVal(); x += variableValue.step()) {
            result.add(x);
          }
          result.add(variableValue.finalVal());
        }
        return result;
      };

      // Step 2 : building of value tuples T from a
      Set<ValueTuple> T = getTupleBuilder(req.valuesKind()).apply(req.variableValues().parallelStream().map(a).toList());
      if(T == null){
        throw new MalformedRequestException(switch (req.valuesKind()){
          case GRID -> "The range of one of the variables is the empty set";
          case LIST -> "Variables' ranges do not have the same magnitude";
        });
      }

      // Step 4 : computation of o from T and E
      return switch (req.kind()) {

        case COUNT -> Server.COMP_REQS_EXECUTOR.submit(T::size).get();

        case MAX -> Server.COMP_REQS_EXECUTOR.submit(() -> {
          double max = Double.NEGATIVE_INFINITY;
          for (Expression e : req.expressions()) {
            for (ValueTuple t : T) {
              Map<String, Double> input = new HashMap<>();  // this map tells us what value to substitute for each variable inside the expression
              int c = 0;
              for (CompRequest.VariableValue v : req.variableValues()) {
                input.put(v.name(), t.values[c++]);
              }
              if (max < e.toRealVariableVectorFunction().apply(input)) {
                max = e.toRealVariableVectorFunction().apply(input);
              }
            }
          }
          return max;
        }).get();

        case MIN -> Server.COMP_REQS_EXECUTOR.submit(() -> {
          double min = Double.POSITIVE_INFINITY;
          for (Expression e : req.expressions()) {
            for (ValueTuple t : T) {
              Map<String, Double> input = new HashMap<>();
              int c = 0;
              for (CompRequest.VariableValue v : req.variableValues()) {
                input.put(v.name(), t.values[c++]);
              }
              if (min > e.toRealVariableVectorFunction().apply(input)) {
                min = e.toRealVariableVectorFunction().apply(input);
              }
            }
          }
          return min;
        }).get();

        case AVG -> Server.COMP_REQS_EXECUTOR.submit(() -> {
          double sum = 0;
          for (ValueTuple t : T) {
            Map<String, Double> input = new HashMap<>();
            int c = 0;
            for (CompRequest.VariableValue v : req.variableValues()) {
              input.put(v.name(), t.values[c++]);
            }
            sum += req.expressions().getFirst().toRealVariableVectorFunction().apply(input);
          }
          return sum / T.size();
        }).get();

      };
    }

    private static Function<List<Set<Double>>, Set<ValueTuple>> getTupleBuilder(CompRequest.ValuesKind valuesKind) {

      Function<List<Set<Double>>, Set<ValueTuple>> cartesianProduct = sets -> {
        for(Set<Double> s : sets){
          if(s == null){  // an empty set makes the set of tuples an empty set
            return null;
          }
        }
        Set<ValueTuple> result = ValueTuple.valueSetToTupleSet(sets.getFirst());
        for (int i = 1; i < sets.size(); i++) {
          Set<ValueTuple> tmpResult = new HashSet<>();
          for (ValueTuple t : result) {
            for (Double d : sets.get(i)) {
              tmpResult.add(new ValueTuple(t, d));
            }
          }
          result = tmpResult;
        }
        return result;
      };

      Function<List<Set<Double>>, Set<ValueTuple>> elementWiseMerge = sets -> {
        for (Set<Double> s : sets) {
          if (s == null || s.size() != sets.getFirst().size()) {
            return null;
          }
        }
        Set<ValueTuple> result = new HashSet<>();
        List<Iterator<Double>> iterators = new ArrayList<>();
        for (Set<Double> set : sets) {
          iterators.add(set.iterator());
        }
        for (int c = 0; c < sets.getFirst().size(); c++) {
          Double[] values = new Double[iterators.size()];
          for (int i = 0; i < values.length; i++) {
            values[i] = iterators.get(i).next();
          }
          result.add(new ValueTuple(values));
        }
        return result;
      };

      return switch (valuesKind) {
        case GRID -> cartesianProduct;
        case LIST -> elementWiseMerge;
      };

    }

    private static boolean exprVarsSubsetOfVarVals(List<Expression> expressions, List<CompRequest.VariableValue> variables) {

      for (Expression e : expressions) {
        Set<String> exprVars = Set.copyOf(e.variables().stream().map(Variable::name).toList());
        Set<String> varValsVars = Set.copyOf(variables.stream().map(CompRequest.VariableValue::name).toList());
        if (!varValsVars.containsAll(exprVars)) {
          return false;
        }
      }

      return true;

    }

    record ValueTuple(Double[] values) {

      ValueTuple(ValueTuple t, double v) {
        this(concat(t.values, new Double[]{v}));
      }

      ValueTuple(ValueTuple t1, ValueTuple t2) {
        this(concat(t1.values, t2.values));
      }

      private static Double[] concat(Double[] arr1, Double[] arr2) {
        Double[] result = Arrays.copyOf(arr1, arr1.length + arr2.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
      }

      static Set<ValueTuple> valueSetToTupleSet(Set<Double> setToTuple) {
        Set<ValueTuple> setTupled = new HashSet<>();
        for (Double d : setToTuple) {
          setTupled.add(valueToTuple(d));
        }
        return setTupled;
      }

      static ValueTuple valueToTuple(Double d) {
        return new ValueTuple(new Double[]{d});
      }

    }

  }

}


