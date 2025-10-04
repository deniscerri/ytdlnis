#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define PORT 65953
#define BUFSIZE 65536

int main(int argc, char *argv[]) {
    char buffer[BUFSIZE];
    int sock;
    struct sockaddr_in server_addr;

    // Collect JS code
    char *code = NULL;
    size_t code_len = 0;

    if (argc > 1) {
        // Join argv[1..] as code (like "node -e 'code'")
        size_t total = 0;
        for (int i = 1; i < argc; i++) total += strlen(argv[i]) + 1;
        code = malloc(total);
        code[0] = '\0';
        for (int i = 1; i < argc; i++) {
            strcat(code, argv[i]);
            if (i < argc - 1) strcat(code, " ");
        }
    } else {
        // Read from stdin
        ssize_t n = read(STDIN_FILENO, buffer, BUFSIZE - 1);
        if (n <= 0) return 1;
        buffer[n] = '\0';
        code = strdup(buffer);
    }

    // Open socket
    sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("socket");
        return 1;
    }

    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(PORT);
    server_addr.sin_addr.s_addr = inet_addr("127.0.0.1");

    if (connect(sock, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        perror("connect");
        close(sock);
        return 1;
    }

    // Send code
    write(sock, code, strlen(code));
    write(sock, "\0", 1);

    // Receive result
    ssize_t n = read(sock, buffer, BUFSIZE - 1);
    if (n > 0) {
        buffer[n] = '\0';
        printf("%s\n", buffer);
    }

    close(sock);
    free(code);
    return 0;
}