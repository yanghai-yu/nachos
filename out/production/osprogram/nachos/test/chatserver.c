#include "syscall.h"
#include "stdio.h"

#define MAX_TEXT_SIZE 1000
#define MAX_CLIENT_SOCKETS 16

int clientSockets[MAX_CLIENT_SOCKETS];
void sendMessageFrom(int sender);

int main(int argc, char* argv[]) {
	int newSocket = 0, i;
	char result[1];
//��ʼ��
	for (i = 0; i < MAX_CLIENT_SOCKETS; i++) {
		clientSockets[i] = -1;
	}
//
	while (1) {
		if (read(stdin, result, 1) != 0) {
			break;
		}

		newSocket = accept(15); // ������socket
		if (newSocket != -1) { // �յ�����socket����ӵ�������
            printf("client %d arrive\n", newSocket);
			clientSockets[newSocket] = newSocket;
		}
		for (i = 0; i < MAX_CLIENT_SOCKETS; i++) {
			if (clientSockets[i] != -1) {
				sendMessageFrom(i);//�Ѹ��û�����Ϣ����������
			}
		}
	}
	
}

void sendMessageFrom(int sender) {
	char messageWord[1];
	char receivedText[MAX_TEXT_SIZE];
	int i, bytesNumW, bytesNumR,receiveEnd = 0;
    
	//��ȡһ���ֽ�
	bytesNumR = read(clientSockets[sender], messageWord, 1);

	// û����Ϣ
	if (bytesNumR == 0)
		return;

	// ������Ϣ�����ر�����������
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
	
    // û����Ϣ
	if (receiveEnd == 0)
		return;
	
    receivedText[receiveEnd] = '\0';
    printf("user says: %s",receivedText);
    
	// �ѽ��յ�����Ϣ���͸����˷�����֮���������
	for (i = 0; i < MAX_CLIENT_SOCKETS; ++i)
		if (i != sender && clientSockets[i] != -1) {
			bytesNumW = write(clientSockets[i], receivedText, receiveEnd);

			// ���ͳ������⣬�ر�����������
			if (bytesNumW != receiveEnd) {
				printf("Unable to write to client %d. Disconnect.", i);
				close(clientSockets[i]);
				clientSockets[i] = -1;
            }
		}
}
