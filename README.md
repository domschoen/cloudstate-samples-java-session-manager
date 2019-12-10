# Cloudstate Session Manager

## Run on Minikube with Cassandra

1. `minikube start --vm-driver=hyperkit --memory 8192 --cpus 2`

3. ```
  eval $(minikube docker-env)
  ```
  
4. ```
   cd couldstate-master
   sbt -Ddocker.tag=dev
   operator/docker:publishLocal
   dockerBuildCassandra publishLocal
exit
   ```
   
5. ```
   kubectl create namespace cloudstate
   ```
   
5. ```
   kubectl apply -n cloudstate -f operator/cloudstate-dev.yaml
   ```

6. Edit config to remove "native-" in images:

   ```
   kubectl edit -n cloudstate configmap cloudstate-operator-config
   ```

7. ```
   cd csamples-java-session-manager
   ```

8. ```
   kubectl apply -f descriptors/store/cassandra-store.yaml
   ```

9. ```
   kubectl apply -f descriptors/cassandra
   ```

10. ```
   sbt -Ddocker.tag=dev
   project java-session-manager
   docker:publishLocal
   exit
   ```

11. ```
    kubectl apply -f descriptors/java-session-manager/java-session-manager.yaml
    ```

12. ```
    kubectl expose deployment sm-deployment --port=8013 --type=NodePort
    minikube service sm-deployment --url
    http://192.168.64.35:30320
    grpcurl -plaintext 192.168.64.35:30320 describe
    ```

13. To try it, launch Postman with

    |        | Value                                                  |
    | ------ | ------------------------------------------------------ |
    | Method | POST                                                   |
    | URL    | http://192.168.64.35:30320/home/MyHome/sessions/create |
    | Body   | {<br/>	"device_id" = "Android Phone"<br/>}          |

    and you will get session for "MyHome":

    ```
    {
        "accountId": "MyHome",
        "sessionId": "1b387cfb-aabe-4b94-ae7e-0f31fc6f909b",
        "expiration": "1575996502"
    }
    ```

    