parallel -j5 "java -jar final.jar {}" ::: std/*.java
rm stat
rm tmp
for file in `ls std|grep ".*\.s"|sed -n "s/\(.*\)\.s$/\1/gp"`
	do echo $file>>stat
spim -stat -file std/$file.s > res 2>tmp
cat tmp| grep "[^:]*:.*[0-9]$">>stat
echo "\n" >>stat
echo $file
cat tmp|grep "^All[^:]*:.*[0-9]$"
diff res std/$file.ans;
done
#cat stat
