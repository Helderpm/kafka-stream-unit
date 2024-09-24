# kafka-stream-unit

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

## How to run

First start the docker-compose which contains ZooKeeper, Kafka, Kafdrop, and MySQL.

```bash
$ docker-compose -f docker-compose.yml up -d
or 
( if you have installed docker compose and using the following command on git bash)
$ docker compose up -d
```