#!/bin/sh
semantic="semantic"
syntactic="syntactic"
good="good"
bad="bad"
result="result.txt"

check(){
    if [ $? -eq $2 ]
    then
	echo "$1 Check OK" >> result.txt
    else
	echo "$1 Check Wrong" >> result.txt
    fi
}

checkAll(){
    for file in `ls $1|grep java`
    do
	java -jar final.jar "$1/$file"
	check "$1/$file" $2
    done
}

###################################################
# Test Good
 if [ -e $result ]
 then
     rm $result
 fi
 checkAll "$syntactic/$good" 0
 checkAll "$syntactic/$bad" 1
 checkAll "$semantic/$good" 0
 checkAll "$semantic/$bad" 1

