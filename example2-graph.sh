#!/bin/sh

PIPE1=reporter_pipe1.log
PIPE2=reporter_pipe2.log
:> $PIPE1
:> $PIPE2

stdbuf -o0 paste -d, \
  <(tail -f $PIPE1 | sed -u -n -E 's/.*ratio=(.*)/\1/p') \
  <(tail -f $PIPE2 | sed -u -n -E 's/.*ratio=(.*)/\1/p') \
  | asciigraph -r -h 20 -w 100 -lb 0.0 -ub 1.0 -sn 2 -sc "blue,red" -sl "pipe1, pipe2"

