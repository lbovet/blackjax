# BLACKJAX

A blackjack game running directly in the Corda nodes.

![anim](https://user-images.githubusercontent.com/692124/104090225-9c306280-5275-11eb-94d4-ab742f134adb.gif)

## Run
```
./gradlew deployNodes
```
in `build/nodes/Alice`, `build/nodes/Bob` and `build/nodes/Alice`:

```
java -jar corda.jar
```

## Design

- The game rules are coded in the states and verified by the contract.
- No state updates. Game, bet and turn states are chained and consumed by the next step.
- The states keep a reference to the previous step.
- The whole game information is built from the whole chain.
- The random values of cards are just simply generated from the transaction hash. No oracle involved.