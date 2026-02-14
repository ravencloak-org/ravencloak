FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR file
COPY build/libs/auth-*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
