
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.berkeley.cs.rise.opaque

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map
import java.util.Arrays


object JobVerificationEngine {
  // An LogEntryChain object from each partition
  var logEntryChains = ArrayBuffer[tuix.LogEntryChain]()
  var sparkOperators = ArrayBuffer[String]()

  def addLogEntryChain(logEntryChain: tuix.LogEntryChain): Unit = {
    logEntryChains += logEntryChain 
  }

  def addExpectedOperator(operator: String): Unit = {
    sparkOperators += operator
  }

  def resetForNextJob(): Unit = {
    sparkOperators.clear
    logEntryChains.clear
  }

  def verify(): Boolean = {
    // Check that all LogEntryChains have been added to logEntries
    // Piece together the sequence of operations / data movement
    println("Expected sequence of operators: " + sparkOperators)
    println("Log Entry Chains Length: " + logEntryChains.length)
    if (sparkOperators.isEmpty) {
      resetForNextJob()
      return true
    }

    val numPartitions = logEntryChains.length

    // FIXME: properly set num ecalls in job
    // Count number of ecalls in "past entries" per partition and ensure they're the same
    var numEcallsInLastPartition = -1
    var startingJobId = -1
    var lastOpInPastEntry = ""
    // var startingEcallIndex = -1
    for (logEntryChain <- logEntryChains) {
      // Iterating through each partition's results
      // Check job ID
      // val finalLogEntry = logEntryChain.currEntries(logEntryChain.currEntriesLength - 1)
      // var firstLogEntry = None

      var numEcallsLoggedInThisPartition = 0
      var prevOp = ""
      for (i <- 0 until logEntryChain.pastEntriesLength) {
        val pastEntry = logEntryChain.pastEntries(i)
        if (pastEntry != Array.empty) {
          if (startingJobId == -1) {
            startingJobId = pastEntry.jobId
          }
          
          // Count the number of ecalls
          if (pastEntry.op != prevOp) {
            numEcallsLoggedInThisPartition += 1
            prevOp = pastEntry.op
            if (pastEntry.op == "nonObliviousAggregateStep2" && numPartitions == 1) {
              // Add an additional ecall for nonObliviousAggregateStep1, as it's not logged when there's only one partition
              println("Added additional ecall for step1")
              numEcallsLoggedInThisPartition += 1
            }
          }
        }
      }
      lastOpInPastEntry = prevOp
      // if (firstLogEntry == None) {
        // startingEcallIndex = logEntryChain.currEntries(0).jobId
      // }
      // val numEcallsInJob = finalLogEntry.jobId + 1 - firstLogEntry.jobId
      if (numEcallsInLastPartition == -1) {
        // numEcallsInLastPartition = numEcallsInJob
        numEcallsInLastPartition = numEcallsLoggedInThisPartition
        // startingEcallIndex = firstLogEntry.jobId
      }
      if (numEcallsLoggedInThisPartition != numEcallsInLastPartition) {
        throw new Exception("All partitions did not perform same number of ecalls")
      }
      // }
    }

    if (startingJobId == -1) {
      // No past entries
      startingJobId = logEntryChains(0).currEntries(0).jobId
    }

  
    var numEcalls = numEcallsInLastPartition 
    if (logEntryChains(0).currEntries(0).op == "nonObliviousAggregateStep2" && numPartitions == 1) {
      // Aggregate Step 1 really messing with us
      numEcalls += 1
    }
    if (logEntryChains(0).currEntries(0).op != lastOpInPastEntry) {
      numEcalls += 1
    }
    println("Num Partitions: " + numPartitions)
    println("Num Ecalls: " + numEcalls)

    var executedAdjacencyMatrix = Array.ofDim[Int](numPartitions * (numEcalls + 1), numPartitions * (numEcalls + 1))
    var ecallSeq = ArrayBuffer[String]()

    // var mapEID = Map[Int, Int]()
    var this_partition = 0

    for (logEntryChain <- logEntryChains) {
      var prevOp = ""
      // println("past entries length: " + logEntryChain.pastEntriesLength)
      for (i <- 0 until logEntryChain.pastEntriesLength) {
        val logEntry = logEntryChain.pastEntries(i)
        val op = logEntry.op
        // println("Ecall: " + op)
        val eid = logEntry.eid
        val ecallIndex = logEntry.jobId - startingJobId
        // println("Log Entry Job ID: " + logEntry.jobId)
        // println("Ecall index: " + ecallIndex)

        val prev_partition = eid

        val row = prev_partition * numEcalls + ecallIndex 
        val col = this_partition * numEcalls + ecallIndex + 1
        // println("Row: " + row + "Col: " + col)

        executedAdjacencyMatrix(row)(col) = 1
        if (op != prevOp) {
          ecallSeq.append(op)
        }
        prevOp = op
      }

      // println("Curr entries length: " + logEntryChain.currEntriesLength)
      for (i <- 0 until logEntryChain.currEntriesLength) {
        val logEntry = logEntryChain.currEntries(i)
        val op = logEntry.op
        // println("Ecall: " + op)
        val eid = logEntry.eid
        val ecallIndex = logEntry.jobId - startingJobId

        // println("Log Entry Job ID: " + logEntry.jobId)
        // println("Ecall index: " + ecallIndex)

        val prev_partition = eid

        // println("Prev partition: " + prev_partition)
        // println("Ecall index: " + ecallIndex)

        val row = prev_partition * numEcalls + ecallIndex 
        val col = this_partition * numEcalls + ecallIndex + 1

        println("Row: " + row + " Col: " + col)
        executedAdjacencyMatrix(row)(col) = 1
        println("Curr Entry Operation: " + op)
        if (op != prevOp) {
          if (op == "nonObliviousAggregateStep2" && numPartitions == 1) {
            ecallSeq.append("nonObliviousAggregateStep1")
          }
          ecallSeq.append(op)
        }
        prevOp = op
      }
      this_partition += 1
    }

    var expectedAdjacencyMatrix = Array.ofDim[Int](numPartitions * (numEcalls + 1), numPartitions * (numEcalls + 1))
    var expectedEcallSeq = ArrayBuffer[String]()
    for (operator <- sparkOperators) {
      if (operator == "EncryptedSortExec" && numPartitions == 1) {
        expectedEcallSeq.append("externalSort")
      } else if (operator == "EncryptedSortExec" && numPartitions > 1) {
        expectedEcallSeq.append("externalSort", "partitionForSort", "findRangeBounds", "sample")
      } else if (operator == "EncryptedProjectExec") {
        expectedEcallSeq.append("project")
      } else if (operator == "EncryptedFilterExec") {
        expectedEcallSeq.append("filter")
      // } else if (operator == "EncryptedAggregateExec" && numPartitions == 1) {
        // // For one partition, all results from nonObliviousAggregateStep1 get thrown out
        // expectedEcallSeq.append("nonObliviousAggregateStep2")
      } else if (operator == "EncryptedAggregateExec") {
        expectedEcallSeq.append("nonObliviousAggregateStep2", "nonObliviousAggregateStep1")
      } else if (operator == "EncryptedSortMergeJoinExec") {
        expectedEcallSeq.append("nonObliviousSortMergeJoin", "scanCollectLastPrimary")
      } else if (operator == "EncryptExec") {
        expectedEcallSeq.append("encrypt")
      } else {
        throw new Exception("Executed unknown operator") 
      }
    }
    expectedEcallSeq = expectedEcallSeq.reverse

    if (!ecallSeq.sameElements(expectedEcallSeq)) {
      println("Ecall seq") 
      ecallSeq foreach { row => row foreach print; println }
      println("Expected Ecall Seq")
      expectedEcallSeq foreach { row => row foreach print; println }
      resetForNextJob()
      return false
    }

    for (i <- 0 until expectedEcallSeq.length) {
      // i represents the current ecall index
      val operator = expectedEcallSeq(i)
      println(operator)
      if (operator == "project") {
        for (j <- 0 until numPartitions) {
          expectedAdjacencyMatrix(j * numEcalls + i)(j * numEcalls + i + 1) = 1
        }
      } else if (operator == "filter") {
        for (j <- 0 until numPartitions) {
          expectedAdjacencyMatrix(j * numEcalls + i)(j * numEcalls + i + 1) = 1
        }
      } else if (operator == "externalSort") {
        for (j <- 0 until numPartitions) {
          expectedAdjacencyMatrix(j * numEcalls + i)(j * numEcalls + i + 1) = 1
        }
      } else if (operator == "sample") {
        for (j <- 0 until numPartitions) {
          // All EncryptedBlocks resulting from sample go to one worker
          // FIXME: which partition?
          expectedAdjacencyMatrix(j * numEcalls + i)(0 * numEcalls + i + 1) = 1
        }
      } else if (operator == "findRangeBounds") {
        // Broadcast from one partition (assumed to be partition 0) to all partitions
        for (j <- 0 until numPartitions) {
          expectedAdjacencyMatrix(0 * numEcalls + i)(j * numEcalls + i + 1) = 1
        }
      } else if (operator == "partitionForSort") {
        // All to all shuffle
        for (j <- 0 until numPartitions) {
          for (k <- 0 until numPartitions) {
            expectedAdjacencyMatrix(j * numEcalls + i)(k * numEcalls + i + 1) = 1
          }
        }
      } else if (operator == "nonObliviousAggregateStep1") {
        if (numPartitions > 1) {
          // Blocks sent to prev and next partition
          for (j <- 0 until numPartitions) {
            var prev = j - 1
            var next = j + 1
            if (j == 0) {
              prev = 0
            } 
            if (j == numPartitions - 1) {
              next = numPartitions - 1
            }
            expectedAdjacencyMatrix(j * numEcalls + i)(prev * numEcalls + i + 1) = 1
            expectedAdjacencyMatrix(j* numEcalls + i)(next * numEcalls + i + 1) = 1
          }
        }
      } else if (operator == "nonObliviousAggregateStep2") {
        for (j <- 0 until numPartitions) {
          println(i)
          println("Setting expected Adjacency matrix at")
          println(j * numEcalls + i)
          println(0 * numEcalls + i + 1)
          expectedAdjacencyMatrix(j * numEcalls + i)(0 * numEcalls + i + 1) = 1
        }
      } else if (operator == "scanCollectLastPrimary") {
        for (j <- 0 until numPartitions) {
          val next = j + 1
          expectedAdjacencyMatrix(j * numEcalls + i)(next * numEcalls + i + 1) = 1
        }
      } else if (operator == "nonObliviousSortMergeJoin") {
        for (j <- 0 until numPartitions) {
          expectedAdjacencyMatrix(j * numEcalls + i)(j * numEcalls + i + 1) = 1
        }
      } else {
        throw new Exception("Job Verification Error creating expected adjacency matrix: operator not supported - " + operator)
      }
    }
  


    
    // Retrieve the physical plan from df.explain()
    // Condense the physical plan to match ecall operations
    // Return whether everything checks out
    println("Expected Adjacency Matrix: ")
    expectedAdjacencyMatrix foreach { row => row foreach print; println }

    println("Executed Adjacency Matrix: ")
    executedAdjacencyMatrix foreach { row => row foreach print; println }

    // if (expectedAdjacencyMatrix sameElements executedAdjacencyMatrix) {
    //   return true
    // } else {
    //   println("False")
    //   return false
    // }
    resetForNextJob()
    for (i <- 0 until numPartitions * (numEcalls + 1); j <- 0 until numPartitions * (numEcalls + 1)) {
      if (expectedAdjacencyMatrix(i)(j) != executedAdjacencyMatrix(i)(j)) {
        return false
      }
      // println(expectedAdjacencyMatrix(i)(j) + "==" + executedAdjacencyMatrix(i)(j))
    }
    return true
  }
}
