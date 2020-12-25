#include "syscall.h"
#include "stdio.h"

#define MAX_TEXT_SIZE 1000
#define false 0
#define true 1

char receivedText[MAX_TEXT_SIZE], sendText[MAX_TEXT_SIZE];
int  receiveEnd, sendEnd, bytesNumR, bytesNumW;

int main(int argc, char* argv[]) {
	int host, socket, exitChat = false;
	char lastByte;
	receiveEnd = 0;
	sendEnd=0;
	//防止用户不输入host address
	if (argc < 2) {
        printf("must supply host address\n");
        return 1;
    }

	host = atoi(argv[1]);
	socket = connect(host, 15);
	printf("Success to connect: %d\n", host);
	// 在用户输入退出的命令"."之前，一直循环，以获得其他用户的消息和发出用户的信息
	while(!exitChat) {
		//接收其他用户的消息
		bytesNumR = read(socket, receivedText + receiveEnd, 1);
		if (bytesNumR == 1) {
			lastByte = receivedText[receiveEnd++];
			if (lastByte == '\n') {  //接收完一行了，可以输出到stdout
				bytesNumW = write(stdout, receivedText, receiveEnd);
				receiveEnd = 0;
			}
		} else if (bytesNumR == -1) {// 读取出现问题
			printf("Server shutdown. \n");
			break;
		}

		//发送自己填写的消息
		sendEnd = 0;
		//从stdin中读取一个字节
		bytesNumR = read(stdin, sendText, 1);
		if (bytesNumR == 1) { //stdin可以读出数据
			lastByte = sendText[0];
			sendEnd++;
			while (lastByte != '\n') {//一直读完一整行或出现错误为止
				bytesNumR = read(stdin, sendText + sendEnd, 1);
				if (bytesNumR == -1) {
					printf("Encounter an error while reading from stdin\n");//出现错误，退出
					exitChat = true;
					break;
				}
				else {//没有错误。可以继续
					sendEnd += bytesNumR;
					lastByte = sendText[sendEnd - 1];
					if (sendEnd == MAX_TEXT_SIZE - 1) {//一次发送的文字有长度限制
						sendText[MAX_TEXT_SIZE - 1] = '\n';
						break;
					}
				}
			}

			// 收到的是退出的命令"."时，退出聊天
			if (sendText[0] == '.' && sendText[1] == '\n') {
				printf("Exit from chatroom\n");
				break;
			} else if(sendText[0] != '\n') {
				bytesNumW = write(socket, sendText, sendEnd);

				if (bytesNumW == -1) {//此时，说明server出现问题
					printf("Something wrong with server.\n");
					break;
				}
			}
		}else if(bytesNumR == -1){
			printf("Encounter an error\n");//出现错误，退出
			exitChat = true;
		}


	}

	close(socket);

	return 0;
}
