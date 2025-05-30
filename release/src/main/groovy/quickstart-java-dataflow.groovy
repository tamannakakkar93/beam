#!groovy
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

t = new TestScripts(args)

/*
 * Run the Dataflow quickstart from https://beam.apache.org/get-started/quickstart-java/
 */

t.describe 'Run Apache Beam Java SDK Quickstart - Dataflow'

  t.intent 'Gets the WordCount Example Code'
    QuickstartArchetype.generate(t)

  t.intent 'Runs the WordCount Code with Dataflow runner'

    // Remove any count files
    t.run """gsutil rm gs://${t.gcsBucket()}/count* || echo 'No files'"""

    // Run the wordcount example with the Dataflow runner
    t.run """mvn compile exec:java -q \
      -Dmaven.wagon.http.retryHandler.class=default \
      -Dmaven.wagon.http.retryHandler.count=5 \
      -Dmaven.wagon.http.pool=false \
      -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
      -Dhttp.keepAlive=false \
      -Dexec.mainClass=org.apache.beam.examples.WordCount \
      -Dexec.args="--runner=DataflowRunner \
                   --project=${t.gcpProject()} \
                   --region=${t.gcpRegion()} \
                   --gcpTempLocation=gs://${t.gcsBucket()}/tmp \
                   --output=gs://${t.gcsBucket()}/counts \
                   --inputFile=gs://apache-beam-samples/shakespeare/*" \
                    -Pdataflow-runner"""

    int retries = 5
    int waitTime = 15 // seconds
    def outputPath = "gs://${t.gcsBucket()}/count"
    def outputFound = false
    for (int i = 0; i < retries; i++) {
      def files = t.run("gsutil ls ${outputPath}*")
      if (files?.trim()) {
        outputFound = true
        break
      }
      t.intent("Output not found yet. Waiting ${waitTime}s...")
      Thread.sleep(waitTime * 1000)
    }

    if (!outputFound) {
      throw new RuntimeException("No output files found for WordCount after ${retries * waitTime} seconds.")
    }

    // Verify wordcount text
    String result = t.run """gsutil cat ${outputPath}* | grep Montague:"""
    t.see "Montague: 47", result

    // Remove count files
    t.run """gsutil rm gs://${t.gcsBucket()}/count*"""

    // Clean up
    t.done()
