#include "syscall.h"
#include "stdio.h"

#define MAX_TEXT_SIZE 1000
#define MAX_CLIENT_SOCKETS 16

int clientSockets[MAX_CLIENT_SOCKETS];
void sendMessageFrom(int sender);

int main(int argc, char* argv[]) {
	int newSocket = 0, i;
	char result[1];
//初始化
	for (i = 0; i < MAX_CLIENT_SOCKETS; i++) {
		clientSockets[i] = -1;
	}
//
	while (1) {
		if (read(stdin, result, 1) != 0) {
			break;
		}

		newSocket = accept(15); // 接收新socket
		if (newSocket != -1) { // 收到了新socket，添加到数组里
            printf("client %d arrive\n", newSocket);
			clientSockets[newSocket] = newSocket;
		}
		for (i = 0; i < MAX_CLIENT_SOCKETS; i++) {
			if (clientSockets[i] != -1) {
				sendMessageFrom(i);//把该用户的消息发给所有人
			}
		}
	}
	
}

void sendMessageFrom(int sender) {
	char messageWord[1];
	char receivedText[MAX_TEXT_SIZE];
	int i, bytesNumW, bytesNumR,receiveEnd = 0;
    
	//读取一个字节
	bytesNumR = read(clientSockets[sender], messageWord, 1);

	// 没有消息
	if (bytesNumR == 0)
		return;

	// 接收消息出错，关闭与他的连接
	if (bytesNumR == -1) {
        printf("connect with client %d shut down \n", sender);
        close(clientSockets[sender]);
        clientSockets[sender] = -1;
        return;
    }

	while ((bytesNumR > -1) && (receiveEnd < MAX_TEXT_SIZE)) {
		receivedText[receiveEnd++] = messageWord[0];
		if (messageWord[0] == '\n')
			break;
		bytesNumR = read(clientSockets[sender], messageWord, 1);
	}
	
    // 没有消息
	if (receiveEnd == 0)
		return;
	
    receivedText[receiveEnd] = '\0';
    printf("user says: %s",receivedText);
    
	// 把接收到的消息发送给除了发送者之外的所有人
	for (i = 0; i < MAX_CLIENT_SOCKETS; ++i)
		if (i != sender && clientSockets[i] != -1) {
			bytesNumW = write(clientSockets[i], receivedText, receiveEnd);

			// 发送出现问题，关闭与他的连接
			if (bytesNumW != receiveEnd) {
				printf("Unable to write to client %d. Disconnect.", i);
				close(clientSockets[i]);
				clientSockets[i] = -1;
            }
		}
}
