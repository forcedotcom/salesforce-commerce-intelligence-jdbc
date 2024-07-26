# cip-client-dataconnector

How to create a fat jar file?

# Extract the JAR file 

jar xvf cip-client-0.1.0-SNAPSHOT.jar

 # Remove signature files

 find . \( -name "*.SF" -o -name "*.RSA" -o -name "*.DSA" \) -delete 

# Repackage the JAR file

go back one directory

 jar cvf new-jar-file.jar -C extracted-dir/ .
