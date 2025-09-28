import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PredictServer {
    private static EasyPredictModelWrapper model;

    public static void main(String[] args) throws Exception {
        String modelPath = System.getenv().getOrDefault("MODEL_PATH", "../Model.zip");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        System.out.println("Loading MOJO model from: " + modelPath);
        model = new EasyPredictModelWrapper(
                new EasyPredictModelWrapper.Config()
                        .setModel(MojoModel.load(modelPath))
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setConvertInvalidNumbersToNa(true)
        );
        System.out.println("Model loaded. Category: " + model.getModelCategory());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/metadata", new MetaHandler());
        server.createContext("/predict", new PredictHandler());
        server.setExecutor(null); // default executor
        server.start();
        System.out.println("PredictServer running on http://localhost:" + port);
        System.out.println("Endpoints: /health, /metadata, /predict");
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendJson(exchange, 405, jsonMsg("error", "Method not allowed"));
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    static class MetaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendJson(exchange, 405, jsonMsg("error", "Method not allowed"));
                return;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("model_category", model.getModelCategory().toString());
            meta.put("response_names", Optional.ofNullable(model.getResponseDomainValues()).orElse(null));
            sendJson(exchange, 200, toJson(meta));
        }
    }

    static class PredictHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, jsonMsg("error", "Method not allowed"));
                return;
            }

            String contentType = getContentType(exchange.getRequestHeaders());
            String body = readBody(exchange.getRequestBody());

            Map<String, Object> inputData = new LinkedHashMap<>();
            
            if (body != null && !body.isEmpty()) {
                if (contentType.contains("application/json")) {
                    try {
                        inputData = parseJson(body);
                    } catch (Exception e) {
                        sendJson(exchange, 400, jsonMsg("error", "Invalid JSON: " + e.getMessage()));
                        return;
                    }
                } else {
                    sendJson(exchange, 400, jsonMsg("error", "Content-Type must be application/json"));
                    return;
                }
            } else {
                sendJson(exchange, 400, jsonMsg("error", "No JSON input provided in request body"));
                return;
            }

            if (inputData.isEmpty()) {
                sendJson(exchange, 400, jsonMsg("error", "Empty JSON object provided"));
                return;
            }

            RowData row = new RowData();
            for (Map.Entry<String, Object> entry : inputData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value != null) {
                    row.put(key, String.valueOf(value));
                }
            }

            try {
                String responseJson = predictToJson(row, inputData);
                sendJson(exchange, 200, responseJson);
            } catch (PredictException e) {
                sendJson(exchange, 500, jsonMsg("error", "Prediction error: " + e.getMessage()));
            }
        }
    }

    private static String predictToJson(RowData row, Map<String, Object> originalParams) throws PredictException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("model_category", model.getModelCategory().toString());
        out.put("input", originalParams);

        switch (model.getModelCategory()) {
            case Binomial: {
                BinomialModelPrediction p = model.predictBinomial(row);
                out.put("predicted_label", p.label);
                Map<String, Double> probs = new LinkedHashMap<>();
                for (int i = 0; i < p.classProbabilities.length; i++) {
                    String cls = model.getResponseDomainValues()[i];
                    probs.put(cls, p.classProbabilities[i]);
                }
                out.put("class_probabilities", probs);
                break;
            }
            case Multinomial: {
                MultinomialModelPrediction p = model.predictMultinomial(row);
                out.put("predicted_label", p.label);
                Map<String, Double> probs = new LinkedHashMap<>();
                for (int i = 0; i < p.classProbabilities.length; i++) {
                    String cls = model.getResponseDomainValues()[i];
                    probs.put(cls, p.classProbabilities[i]);
                }
                out.put("class_probabilities", probs);
                break;
            }
            case Regression: {
                RegressionModelPrediction p = model.predictRegression(row);
                out.put("predicted_value", p.value);
                break;
            }
            case Ordinal: {
                OrdinalModelPrediction p = model.predictOrdinal(row);
                out.put("predicted_label", p.label);
                out.put("label_index", p.labelIndex);
                break;
            }
            case Clustering: {
                ClusteringModelPrediction p = model.predictClustering(row);
                out.put("cluster", p.cluster);
                break;
            }
            case AutoEncoder: {
                AutoEncoderModelPrediction p = model.predictAutoEncoder(row);
                out.put("reconstructed", p.reconstructed);
                break;
            }
            case AnomalyDetection: {
                AnomalyDetectionPrediction p = model.predictAnomalyDetection(row);
                out.put("normalized_score", p.normalizedScore);
                out.put("score", p.score);
                out.put("is_anomaly", p.isAnomaly);
                break;
            }
            case CoxPH: {
                CoxPHModelPrediction p = model.predictCoxPH(row);
                out.put("value", p.value);
                break;
            }
            case DimReduction: {
                DimReductionModelPrediction p = model.predictDimReduction(row);
                out.put("dimensions", p.dimensions);
                break;
            }
            default: {
                out.put("warning", "Model category not explicitly handled.");
            }
        }

        return toJson(out);
    }

    private static String getContentType(Headers headers) {
        String ct = headers.getFirst("Content-Type");
        return ct == null ? "" : ct.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String val = kv.length > 1 ? urlDecode(kv[1]) : "";
            if (!key.isEmpty()) params.put(key, val);
        }
        return params;
    }

    private static Map<String, Object> parseJson(String json) {
        // Simple JSON parser for basic objects (no external dependencies)
        Map<String, Object> result = new LinkedHashMap<>();
        json = json.trim();
        
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new RuntimeException("JSON must be an object starting with { and ending with }");
        }
        
        json = json.substring(1, json.length() - 1).trim(); // Remove { }
        
        if (json.isEmpty()) {
            return result;
        }
        
        // Split by commas, but be careful about quoted strings
        List<String> pairs = splitJsonPairs(json);
        
        for (String pair : pairs) {
            String[] kv = splitJsonKeyValue(pair);
            if (kv.length == 2) {
                String key = parseJsonString(kv[0].trim());
                Object value = parseJsonValue(kv[1].trim());
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    private static List<String> splitJsonPairs(String json) {
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;
        
        for (char c : json.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                current.append(c);
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                pairs.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            pairs.add(current.toString().trim());
        }
        
        return pairs;
    }
    
    private static String[] splitJsonKeyValue(String pair) {
        boolean inQuotes = false;
        boolean escaped = false;
        
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ':' && !inQuotes) {
                return new String[]{pair.substring(0, i), pair.substring(i + 1)};
            }
        }
        
        return new String[]{pair}; // No colon found
    }
    
    private static String parseJsonString(String str) {
        str = str.trim();
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
            // Basic unescaping
            str = str.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return str;
    }
    
    private static Object parseJsonValue(String value) {
        value = value.trim();
        
        if (value.equals("null")) {
            return null;
        } else if (value.equals("true")) {
            return true;
        } else if (value.equals("false")) {
            return false;
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            return parseJsonString(value);
        } else {
            // Try to parse as number
            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                } else {
                    return Long.parseLong(value);
                }
            } catch (NumberFormatException e) {
                return value; // Return as string if not a valid number
            }
        }
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String readBody(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String jsonMsg(String key, String value) {
        return "{\"" + esc(key) + "\":\"" + esc(value) + "\"}";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJson(Object obj) {
        // Minimal JSON builder to avoid external dependencies
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + esc((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return String.valueOf(obj);
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Object eObj : ((Map<?, ?>) obj).entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) eObj;
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(String.valueOf(e.getKey()))).append(":").append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object v : (Collection<?>) obj) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(v));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(java.lang.reflect.Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return toJson(String.valueOf(obj));
    }
}
