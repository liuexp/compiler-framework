all:
#	git add .
	git commit -a -m "this is an auto commit for final test."
	git push
testSyn:
	parallel "java -jar mid.jar {};echo \$?;" ::: compiler-testcases/syntactic/good/*.java |less
	parallel "java -jar mid.jar {}" ::: compiler-testcases/syntactic/bad/*.java 

testSem:
	parallel "java -jar mid.jar {}" ::: compiler-testcases/semantic/good/*.java |less
	parallel "java -jar mid.jar {}" ::: compiler-testcases/semantic/bad/*.java 

testTrans:
	parallel "java -jar javac.jar {}" ::: compiler-testcases/semantic/good/*.java |less
