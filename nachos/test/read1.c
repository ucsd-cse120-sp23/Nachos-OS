/*
 * write1.c
 *
 * Write a string to stdout, one byte at a time.  Does not require any
 * of the other system calls to be implemented.
 *
 * Geoff Voelker
 * 11/9/15
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *str = "\nroses are red\nviolets are blue\nI love Nachos\nand so do you\n\n";
    char *str1 = str;
    
    while (*str1) {
	    int w = write(1, str1, 1);
	    if (w != 1) {
	        printf ("failed to write character (w = %d)\n", w);
	        exit (-1);
	    }
	    str1++;
    }

    // while (*str) {
	//     int r = write(0, str, 1);
	//     if (r != 1) {
	//         printf ("failed to write character (r = %d)\n", r);
	//         exit (-1);
	//     }
	//     str++;
    // }

    // int r = read (0, str)
		int r = read(1, 2, 10);
	    if (r != 1) {
	        printf ("failed to write character (r = %d)\n", r);
	        exit (-1);
	    }
    return 0;
}