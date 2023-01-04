#!/bin/bash


#This step assigns the linux user name to the username variable
username=$(whoami)

#This step sets the git user for the service core updation process
#git config --global user.email "syne-gitlab@synechron.com"
#git config --global user.name "syne-gitlab"

BASE_DIR=$PWD

#Initialize default git branches
corda_branch=master
spring_branch=master
ui_branch=master

#function to clean up existing services
clean_services(){
 echo "Clean services (Kill all processes)"
 ports=(1499 5123)
 echo $ports
    for port in "${ports[@]}"
    do
        #echo "Inside for loop"  
        PSID=$(lsof -i | grep "$port (LISTEN)" | awk '{print $2}')
        #echo -e "\e[31mpsid\e[0m"
        echo $PSID
        sudo kill -9 $PSID
    done
      #Stop and kill the process on the port used for corada app.
}


#Git pull
cserv_git() {
  echo $PWD  
  echo "Taking git pull of obligation-cordapp-template..."
  git stash
  git pull
}

#Git clone/pull
bserv_git() {
#If directory already exists then pull. Else clone
if [ -d "cordapp-monitoring-client" ]; then
echo "Taking git pull of cordapp-monitoring-client..."
    cd cordapp-monitoring-client
    git stash
    git pull
    cd ..;
else 
    git clone https://gitlab.com/syne-corda/cordapp-monitoring-client.git -b $spring_branch;
    echo "Taking git clone of cordapp-monitoring-client from branch:" $spring_branch
fi
}

#Git clone/pull
userv_git() {

#If directory already exists then pull. Else clone
if [ -d "cordapp-monitoring-ui" ]; then
echo "Taking git pull of cordapp-monitoring-ui..."
    cd cordapp-monitoring-ui
    git stash
    git pull
    cd ..;
else
    git clone https://gitlab.com/syne-corda/cordapp-monitoring-ui.git -b $ui_branch;
    echo "Taking git clone of cordapp-monitoring-ui from branch:" $ui_branch
fi
}

#Function for build and install obligation-cordapp-template
corda_build_install(){
    #pull latest code from git lab
    cserv_git   
    bash ./gradlew clean build -x test
    bash ./gradlew install -x test 
    cd ${BASE_DIR}
}

#Function for deploying business service 
spring_boot_build(){
    #pull latest code from git lab
    echo -e "\e[31mbserv call\e[0m"
    cd ..;
    bserv_git
    cd cordapp-monitoring-client
    bash ./gradlew clean build -x test
    echo "build monitor-client docker image...."
    sudo docker build -t monitor-client .
    cd ${BASE_DIR}
}

#Function for deploying UI service 
ui_build(){
    echo -e "\e[31muserv call\e[0m"
    cd ..;
    userv_git
    cd cordapp-monitoring-ui
    npm i
    ng build --prod 
    echo "build monitor-ui docker image...."
    sudo  docker build -t monitor-ui . 
    cd ${BASE_DIR}
}

cpy_mjar_to_cordapp(){
   echo "Copy jar file to cordapps...." 
   read -p "Enter cordapp absolute path : " CORDAPP_DIR 
   echo $CORDAPP_DIR
   sudo find ${CORDAPP_DIR} -name "cordapps" -exec cp ${BASE_DIR}/cordapp-monitoring/build/libs/cordapp-monitoring-0.2-SNAPSHOT.jar {} \;
}

#Function for run docker-compose 
docker_compose_up(){
   cd ..;
   cd cordapp-monitoring-client
   echo "docker compose up..."
   sudo  docker-compose up
   cd ${BASE_DIR}
}

clean=false
build=false
run=false
m=$#

if [ $m == 0 ];then
  echo "one or more command line arguments required - 1.clean 2.build 3.run"
fi

for ((i=1;i<=$m;i++));do 
    if [ "${!i}" == 'clean' ];then 
      clean=true 
    elif [ "${!i}" == 'build' ];then 
      build=true
    elif [ "${!i}" == 'run' ];then 
      run=true  
    else "Invalid command line arguments"
fi
done

if $clean ;then
   echo "Clean"
   clean_services  
fi
if $build ;then
   echo "Build"
   corda_build_install
   spring_boot_build
   ui_build
   cpy_mjar_to_cordapp     
fi
if $run ;then
   echo "Run"  
   docker_compose_up
fi

