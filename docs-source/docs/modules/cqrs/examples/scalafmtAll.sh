#!/bin/bash

pushd shopping-analytics-service-scala
sbt scalafmtAll
popd

pushd shopping-cart-service-scala
sbt scalafmtAll
popd

pushd shopping-order-service-scala
sbt scalafmtAll
popd

