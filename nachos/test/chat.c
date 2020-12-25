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
	//��ֹ�û�������host address
	if (argc < 2) {
        printf("must supply host address\n");
        return 1;
    }

	host = atoi(argv[1]);
	socket = connect(host, 15);
	printf("Success to connect: %d\n", host);
	// ���û������˳�������"."֮ǰ��һֱѭ�����Ի�������û�����Ϣ�ͷ����û�����Ϣ
	while(!exitChat) {
		//���������û�����Ϣ
		bytesNumR = read(socket, receivedText + receiveEnd, 1);
		if (bytesNumR == 1) {
			lastByte = receivedText[receiveEnd++];
			if (lastByte == '\n') {  //������һ���ˣ����������stdout
				bytesNumW = write(stdout, receivedText, receiveEnd);
				receiveEnd = 0;
			}
		} else if (bytesNumR == -1) {// ��ȡ��������
			printf("Server shutdown. \n");
			break;
		}

		//�����Լ���д����Ϣ
		sendEnd = 0;
		//��stdin�ж�ȡһ���ֽ�
		bytesNumR = read(stdin, sendText, 1);
		if (bytesNumR == 1) { //stdin���Զ�������
			lastByte = sendText[0];
			sendEnd++;
			while (lastByte != '\n') {//һֱ����һ���л���ִ���Ϊֹ
				bytesNumR = read(stdin, sendText + sendEnd, 1);
				if (bytesNumR == -1) {
					printf("Encounter an error while reading from stdin\n");//���ִ����˳�
					exitChat = true;
					break;
				}
				else {//û�д��󡣿��Լ���
					sendEnd += bytesNumR;
					lastByte = sendText[sendEnd - 1];
					if (sendEnd == MAX_TEXT_SIZE - 1) {//һ�η��͵������г�������
						sendText[MAX_TEXT_SIZE - 1] = '\n';
						break;
					}
				}
			}

			// �յ������˳�������"."ʱ���˳�����
			if (sendText[0] == '.' && sendText[1] == '\n') {
				printf("Exit from chatroom\n");
				break;
			} else if(sendText[0] != '\n') {
				bytesNumW = write(socket, sendText, sendEnd);

				if (bytesNumW == -1) {//��ʱ��˵��server��������
					printf("Something wrong with server.\n");
					break;
				}
			}
		}else if(bytesNumR == -1){
			printf("Encounter an error\n");//���ִ����˳�
			exitChat = true;
		}


	}

	close(socket);

	return 0;
}
