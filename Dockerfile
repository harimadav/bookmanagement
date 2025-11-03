# Step 1: Use official OpenJDK image
FROM openjdk:17-jdk-slim

# Step 2: Set working directory
WORKDIR /app

# Step 3: Copy backend files
COPY . .

# Step 4: Compile Server.java (include gson jar)
RUN javac -cp ".:libs/gson-2.10.1.jar" Server.java

# Step 5: Expose port 5000
EXPOSE 5000

# Step 6: Run server
CMD ["java", "-cp", ".:libs/gson-2.10.1.jar", "Server"]
