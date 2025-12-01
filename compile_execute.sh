#!/bin/bash
javac -d out $(find src -name "*.java")
cd out
java com/ossimulator/Main