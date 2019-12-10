# Cloudstate Session Manager
## Description
This example has a Cloudstate user function containing 2 services. It gives you an example of a command fowarding to another service.

It represent a Session Manager which manage Home Account like you may have when you access a Video Streaming platform. You buy an account which allows a limited number of people to watch video simultanously. For each video you start to watch from a device, the device needs a session. It means that the device can ask the "Home" service for:
- session creation
- session renewal (HeartBeat)
- session termination (TearDown)

Note: If the device tries to ask for an extra session above the max number of sessions of the Home account, then you will have an error.

There is a second service called "Device" which manage the device / account relationship. This is needed because a device may ask for a session without the account ID but with the device ID. This is the scenario:
1. The deviced send a createSession with device ID to the "Device" service
2. The "Device" service knows the account ID for the device (it should have been set beforehand to the "Device" service)
3. With the account ID, the "Device" account forward the request to the "Home" service which will return the new session created to the device.

## Run on Minikube with Cassandra
To run this example, you need to run some command in this project: https://github.com/cloudstateio/cloudstate

1. `minikube start --vm-driver=hyperkit --memory 8192 --cpus 2`
2. ```eval $(minikube docker-env)```
3. ```
   cd couldstate-master
   sbt -Ddocker.tag=dev
   operator/docker:publishLocal
   dockerBuildCassandra publishLocal
   exit
   ```
5. ```kubectl create namespace cloudstate```
6. ```kubectl apply -n cloudstate -f operator/cloudstate-dev.yaml```
7. Edit config to remove "native-" in images:```kubectl edit -n cloudstate configmap cloudstate-operator-config```
8. ```cd csamples-java-session-manager```
9. ```kubectl apply -f descriptors/store/cassandra-store.yaml```
10. ```kubectl apply -f descriptors/cassandra```
11. ```sbt -Ddocker.tag=dev
    project java-session-manager
    docker:publishLocal
    exit
    ```
12. ```kubectl apply -f descriptors/java-session-manager/java-session-manager.yaml```
13. ```
    kubectl expose deployment sm-deployment --port=8013 --type=NodePort
    minikube service sm-deployment --url
    http://192.168.64.35:30320
    grpcurl -plaintext 192.168.64.35:30320 describe
    ```
14. To try it, launch Postman with:

    |        | Value                                                  |
    | ------ | ------------------------------------------------------ |
    | Method | POST                                                   |
    | URL    | http://192.168.64.35:30320/home/MyHome/sessions/create |
    | Body   | {<br/>	"device_id" = "Android Phone"<br/>}           |

    and you will get session for "MyHome":

    ```
    {
        "accountId": "MyHome",
        "sessionId": "1b387cfb-aabe-4b94-ae7e-0f31fc6f909b",
        "expiration": "1575996502"
    }
    ```

    
