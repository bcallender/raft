#! /bin/bash

cd ~
mkdir ~/.local/maven
wget http://apache.mirrors.pair.com/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
tar -xf apache-maven-3.3.9-bin.tar.gz -C ~/.local/maven --strip-components=1
printf '\n%s\n%s\n%s\n%s' 'export PATH=~/.local/bin:$PATH' 'export M2_HOME=~/.local/maven' 'export M2=$M2_HOME/bin' 'export PATH=$M2:$PATH' >> ~/.bashrc
source ~/.bashrc