#include "stdio.h"
#include "stdlib.h"
#include "memcpy.c"
// #include <stdio.h>
// #include <stdlib.h>

int main(int argc, char** argv)
{

  char *str = "\nroses are red\nviolets are blue\nI love Nachos\nand so do you\n\n";

  printf("str: %s\n", str);
  int i;

  printf("%d arguments\n", argc);
  
  for (i=0; i<argc; i++)
    // printf("arg %d: %s\n", i, argv[i]);
    printf("arg%d strlen(%s): %d\n", i, argv[i], strlen(argv[i]));


  // char * buf1 = (char*) malloc(200 * sizeof(char));
  // char* buf1;
  // memcpy(str, buf1, sizeof(*str));
  // printf("buf1: %s\n", buf1);
  // for (i=0; i<argc; i++)
  //   printf("arg %d: %s\n", i, argv[i]);

  return 0;
}