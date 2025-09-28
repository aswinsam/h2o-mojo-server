# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy the H2O genmodel jar and model to app root
COPY h2o-genmodel.jar .
COPY Model.zip .

# Copy Java source and compile to build directory
COPY PredictServer.java .
RUN mkdir build && \
    javac -cp ".:h2o-genmodel.jar" -d build PredictServer.java

# Change working directory to build for execution
WORKDIR /app/build

# Expose the port
EXPOSE 8080

# Set environment variables with defaults
ENV MODEL_PATH=../Model.zip
ENV PORT=8080

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

# Run the server from build directory
CMD ["sh", "-c", "java -cp ../h2o-genmodel.jar:. PredictServer"]
