#!/bin/bash
javac -d out (Get-ChildItem -Recurse src\*.java).FullName
cd out
java com/ossimulator/Main