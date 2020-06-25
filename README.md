# MockSniffer: Characterizing and Recommending Mocking Decisions for Unit Tests

## Environment Setup
The following JDKs are required:
- JDK 8: Required for Soot to load and analyze the binaries
- JDK 11: Required to run MockSniffer itself

## Building the project
Just simply execute `mvn package` and find the `mocksniffer.jar` in `target`.


## Usage

### Obtaining the ML model

- Download the model from releases
- Extract data from the open source projects and train the model

### Step 1: Extract dependency pairs from the target project
```shell script
java -jar mocksniffer.jar extract-dataset \
    --repo ./repos/hadoop \ # The root directory od the target projects
    -rt /usr/lib/jvm/java-8-openjdk-amd64 \ # The root folder of JDK 8
    -o ./dataset.csv \ # Output file
    -pp # Number of the projects to extract in parallel (10 by default)
```

### Step 2: Extract features of the dependency pairs
```shell script
java -jar mocksniffer.jar extract-features \
    --repo ./repos/hadoop \ # The root directory od the target projects
    --dataset ./dataset.csv \ # The dataset extracted in previous step
    -rt /usr/lib/jvm/java-8-openjdk-amd64 \ # The root folder of JDK 8
    -o ./output.csv \ # Output file
    -pp # Number of the projects to extract in parallel (10 by default)
```

### Step 3: Run the prediction process
```shell script
java -jar mocksniffer.jar batch-predit \
    --repo ./repos/hadoop \ # The root directory od the target projects
    --input ./dataset.csv \ # The dataset extracted in step 1
    --model ./model.pmml \ # The model file
    -o ./prediction.csv \ # Output file
```
