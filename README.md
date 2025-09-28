# H2O MOJO Prediction Server

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A lightweight, production-ready HTTP server that serves H2O MOJO models via REST API. Built with zero external dependencies beyond H2O's genmodel library.

## Features

- 🚀 **Fast & Lightweight**: Minimal Java HTTP server with no frameworks
- 🔄 **Model Agnostic**: Works with any H2O MOJO model (AutoML, GBM, RF, etc.)
- 🐳 **Docker Ready**: Containerized deployment with multi-stage builds
- � **JSON API**: Clean REST endpoints with JSON input/output
- �� **Easy Deployment**: Simple configuration via environment variables
- 🛡️ **Production Ready**: Proper error handling and health checks

## Quick Start

### Prerequisites

- **Java 17+** (OpenJDK recommended)
- **H2O MOJO model** (`.zip` file exported from H2O)
- **h2o-genmodel.jar** (download from [H2O releases](https://github.com/h2oai/h2o-3/releases))

> Note: `h2o-genmodel.jar` and your `Model.zip` are required to build/run but are **not included** in this open-source repository and are **git ignored**. You must provide them locally as described below.

### 1. Clone and Setup

```bash
git clone <repository-url>
cd h2o-mojo-server

# Place your files (required, not versioned)
cp /path/to/your/model.zip ./Model.zip
cp /path/to/h2o-genmodel.jar ./h2o-genmodel.jar
```

### 2. Build

#### Linux/macOS
```bash
# Create build directory
mkdir -p build

# Compile
javac -cp ".:h2o-genmodel.jar" -d build PredictServer.java
```

#### Windows
```cmd
# Create build directory
mkdir build

# Compile
javac -cp ".;h2o-genmodel.jar" -d build PredictServer.java
```

### 3. Run

#### Linux/macOS
```bash
cd build
java -cp "../h2o-genmodel.jar:." PredictServer
```

#### Windows
```cmd
cd build
java -cp "..\h2o-genmodel.jar;." PredictServer
```

Server starts on `http://localhost:8080`

## API Reference

### Endpoints

| Method | Endpoint    | Description                    |
|--------|-------------|--------------------------------|
| GET    | `/health`   | Health check                   |
| GET    | `/metadata` | Model information              |
| POST   | `/predict`  | Make predictions               |

### Health Check
```bash
curl http://localhost:8080/health
```
```json
{"status": "ok"}
```

### Model Metadata
```bash
curl http://localhost:8080/metadata
```
```json
{
  "model_category": "Regression",
  "response_names": null
}
```

### Make Predictions
```bash
curl -X POST http://localhost:8080/predict \
  -H "Content-Type: application/json" \
  -d '{"feature1": 123, "feature2": "category", "feature3": 45.6}'
```

**Response:**
```json
{
  "model_category": "Regression",
  "input": {"feature1": 123, "feature2": "category", "feature3": 45.6},
  "predicted_value": 78.42
}
```

### Response Formats by Model Type

#### Regression
```json
{
  "model_category": "Regression",
  "input": {...},
  "predicted_value": 78.42
}
```

#### Binary Classification
```json
{
  "model_category": "Binomial",
  "input": {...},
  "predicted_label": "Yes",
  "class_probabilities": {
    "No": 0.23,
    "Yes": 0.77
  }
}
```

#### Multi-class Classification
```json
{
  "model_category": "Multinomial",
  "input": {...},
  "predicted_label": "ClassA",
  "class_probabilities": {
    "ClassA": 0.65,
    "ClassB": 0.25,
    "ClassC": 0.10
  }
}
```

## Docker Deployment

### Quick Run

> **Important**: The `Dockerfile` uses `COPY h2o-genmodel.jar` and `COPY Model.zip`. Ensure both files exist next to the `Dockerfile` before building the image, or the build will fail. Alternatively, you can mount your model at runtime (see below) but the `h2o-genmodel.jar` must still be present at build time.

```bash
# Build image
docker build -t h2o-prediction-server .

# Run container
docker run -p 8080:8080 h2o-prediction-server
```

### Custom Model
```bash
# Mount your own model
docker run -p 8080:8080 \
  -v "$(pwd)/your-model.zip:/app/Model.zip" \
  h2o-prediction-server
```

### Environment Variables
```bash
# Custom port and model path
docker run -p 9090:9090 \
  -e PORT=9090 \
  -e MODEL_PATH=/app/custom-model.zip \
  -v "$(pwd)/custom-model.zip:/app/custom-model.zip" \
  h2o-prediction-server
```

## Configuration

### Environment Variables

| Variable     | Default        | Description                    |
|--------------|----------------|--------------------------------|
| `PORT`       | `8080`         | Server port                    |
| `MODEL_PATH` | `../Model.zip` | Path to MOJO model file        |

### Examples

#### Linux/macOS
```bash
export MODEL_PATH="/path/to/your/model.zip"
export PORT=9090
cd build
java -cp "../h2o-genmodel.jar:." PredictServer
```

#### Windows
```cmd
set MODEL_PATH=C:\path\to\your\model.zip
set PORT=9090
cd build
java -cp "..\h2o-genmodel.jar;." PredictServer
```

## Project Structure

```
├── PredictServer.java      # Main server implementation
├── h2o-genmodel.jar       # H2O inference library (user-provided, not in repo)
├── Model.zip              # Your H2O MOJO model (user-provided, not in repo)
├── build/                 # Compiled classes (auto-generated)
├── Dockerfile             # Container definition
├── .gitignore             # Git ignore rules
└── README.md              # This file
```
 
## Development

### Building from Source
1. Ensure Java 17+ is installed
2. Download `h2o-genmodel.jar` from H2O releases (place it alongside the sources)
3. Place your MOJO model as `Model.zip` (alongside the sources)
4. Follow build instructions above

### Testing
```bash
# Health check
curl http://localhost:8080/health

# Metadata
curl http://localhost:8080/metadata

# Sample prediction (adjust features for your model)
curl -X POST http://localhost:8080/predict \
  -H "Content-Type: application/json" \
  -d '{"feature1": 1, "feature2": "test"}'
```

## Deployment

### Production Considerations

1. **Resource Limits**: Set appropriate JVM heap size
   ```bash
   java -Xmx2g -cp "../h2o-genmodel.jar:." PredictServer
   ```

2. **Logging**: Redirect output for production
   ```bash
   java -cp "../h2o-genmodel.jar:." PredictServer > server.log 2>&1
   ```

3. **Process Management**: Use systemd, supervisor, or similar
4. **Load Balancing**: Run multiple instances behind a load balancer
5. **Monitoring**: Monitor `/health` endpoint for uptime

### Docker Production
```bash
# Build image with health checks
docker build -t h2o-prediction-server:latest .

# Run with resource limits and health monitoring
docker run -d \
  --name h2o-server \
  --memory=2g \
  --cpus=2 \
  -p 8080:8080 \
  --restart=unless-stopped \
  h2o-prediction-server:latest

# Check container health status
docker ps  # Shows health status in STATUS column
```

### Health Check Configuration
The Docker image includes automatic health checks:
- **Interval**: 30 seconds between checks
- **Timeout**: 10 seconds per check
- **Start Period**: 40 seconds initial grace period
- **Retries**: 3 failed attempts before marking unhealthy

```bash
# View health check logs
docker inspect h2o-server --format='{{json .State.Health}}'
```

## Troubleshooting

### Common Issues

1. **Model not found**: Ensure `Model.zip` exists and `MODEL_PATH` is correct
2. **Java not found**: Verify Java 17+ is installed and in PATH
3. **Port in use**: Change port with `PORT` environment variable
4. **Memory issues**: Increase JVM heap size with `-Xmx` flag

### Debug Mode
```bash
# Enable verbose output
java -verbose:gc -cp "../h2o-genmodel.jar:." PredictServer
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [H2O.ai](https://h2o.ai/) for the excellent machine learning platform
- Java community for the robust ecosystem
