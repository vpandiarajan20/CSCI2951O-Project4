#!/bin/bash

########################################
############# CSCI 2951-O ##############
########################################
E_BADARGS=65
if [ $# -ne 1 ]
then
	echo "Usage: `basename $0` <input>"
	exit $E_BADARGS
fi
	
input=$1
# export the solver libraries into the path
export LD_LIBRARY_PATH="$LD_LIBRARY_PATH:'/c/Program Files/IBM/ILOG/CPLEX_Studio2211/cplex/bin/x64_win64/cplex.exe':'/c/Program Files/IBM/ILOG/CPLEX_Studio2211/cpoptimizer/bin/x64_win64/cpoptimizer.exe'"
# add the solver jar to the classpath and run
java -cp "/c/Program Files/IBM/ILOG/CPLEX_Studio2211/cplex/lib/cplex.jar:/c/Users/vpand/Documents/College/Spring_2024/CS2951-O/CSCI2951O-Project4/src" solver.ip.Main "$input"
