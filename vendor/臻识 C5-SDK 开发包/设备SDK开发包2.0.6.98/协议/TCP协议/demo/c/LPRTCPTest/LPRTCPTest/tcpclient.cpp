//说明：通信协议的文档说明请参考《识别结果传输协议.pdf》
//修改时间:2014-12-23
#if defined(__WIN32__) || defined(_WIN32) || defined(_WIN32_WCE)
/* Windows */
#if defined(WINNT) || defined(_WINNT) || defined(__BORLANDC__) || defined(__MINGW32__) || defined(_WIN32_WCE) || defined (_MSC_VER)
#define _MSWSOCK_
#define FD_SETSIZE      1024
#include <winsock2.h>
#include <ws2tcpip.h>
#endif
#include <windows.h>
#include <errno.h>
#include <string.h>

#define closeSocket closesocket
#ifdef EWOULDBLOCK
#undef EWOULDBLOCK
#endif
#ifdef EINPROGRESS
#undef EINPROGRESS
#endif
#ifdef EAGAIN
#undef EAGAIN
#endif
#ifdef EINTR
#undef EINTR
#endif
#define EWOULDBLOCK WSAEWOULDBLOCK
#define EINPROGRESS WSAEWOULDBLOCK
#define EAGAIN WSAEWOULDBLOCK
#define EINTR WSAEINTR

#else
/* Unix */


#include <fcntl.h>
#include <error.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <strings.h>
#include <ctype.h>
#include <stdint.h>
#if defined(_QNX4)
#include <sys/select.h>
#include <unix.h>

#endif

#define closeSocket close
#define SOCKET_ERROR -1
#endif

#include <stdio.h>
#include <stdlib.h>
#include <time.h>
//////////////////////////////////////////////////////////////////////////
#ifdef _USE_JSONCPP_LIB
#include <json/json.h>
using namespace  std;
#endif
#include "VzClientSDK_LPDefine.h"
//////////////////////////////////////////////////////////////////////////
#define DEFAULT_PORT 8131
#define DEFAULT_IP "192.168.4.132"
enum
{
	TCP_BLOCK = 0,
	TCP_UNBLOCK
};
enum
{
	DISABLE_PUSH = 0,
	ENABLE_PUSH
};
enum
{
	FORMAT_BINARY = 0,
	FORMAT_JSON
};
enum
{
	DISABLE_IMAGE = 0,
	ENABLE_IMAGE
};
enum
{
    IMAGE_TYPE_BIG = 0,
    IMAGE_TYPE_SMALL,
    IMAGE_TYPE_BOTH,
    IMAGE_TYPE_DISABLE = 4
};
enum
{
	IO_OUT1 = 0,
	IO_OUT2,
	IO_OUT3,
	IO_OUT4
};
enum
{
	IO_OUT_LOW = 0,
	IO_OUT_HIGH,
	IO_OUT_SQUARE_HIGH //开闸
};
enum
{
    BLOCK_TYPE_JSON_RESULT = 0,
    BLOCK_TYPE_BIN_RESULT,
    BLOCK_TYPE_IMAGE_DATA, 
    BLOCK_TYPE_IMAGE_FRAGMENT_DATA
};
typedef struct 
{
    unsigned char magic[2];
    unsigned char type;
    unsigned char reserved;
    unsigned int length;
}BLOCK_HEADER;
//const int port=5298;
//const char szIp[32]="192.168.1.225";
//cmd json str
// get serial number
const char getsn[] = "{"
						"\"cmd\" : \"getsn\""
					"}";
//enable image transformation from the ivs result
const char enable[] = "{"
						"\"cmd\" : \"ivsresult\","
						"\"image\" : true"
					"}";
//disable image transformation from the ivs result
const char disable[] = "{"
						"\"cmd\" : \"ivsresult\","
						"\"image\" : false"
					"}";
const char getivsresult[] = "{"
						"\"cmd\" : \"getivsresult\","
						"\"image\" : true"
					"}";
static const char offLineRegister[] ="{\n"
						"\"cmd\":\"offline\",\n"
						"\"interval\":2\n"
					"}";
//static const char offLineNotify[] =		"{\n"
//						"\"cmd\":\"offline_notify\",\n"
//						"\"id\":2\n"
//						"}";
static const char triggerIVSResult[] =		"{\n"
						"\"cmd\":\"trigger\"\n"						
						"}";
static const char getOfflineRecord[] =		"{\n"
						"\"cmd\":\"get_offline_record\"\n"						
						"}";
static const char offLineNotify[] =		"{\n"
						"\"cmd\":\"ivs_result\",\n"
						"\"response\":\"ok\""
						"}";
#if defined(__WIN32__) || defined(_WIN32)
#define WS_VERSION_CHOICE1 0x202/*MAKEWORD(2,2)*/
#define WS_VERSION_CHOICE2 0x101/*MAKEWORD(1,1)*/
int initializeWinsockIfNecessary(void)
{
	static int _haveInitializedWinsock = 0;
	WSADATA	wsadata;

	if (!_haveInitializedWinsock) 
	{
		if ((WSAStartup(WS_VERSION_CHOICE1, &wsadata) != 0)
		    && ((WSAStartup(WS_VERSION_CHOICE2, &wsadata)) != 0)) 
		{
			return 0; /* error in initialization */
		}
    	if ((wsadata.wVersion != WS_VERSION_CHOICE1)
    	    && (wsadata.wVersion != WS_VERSION_CHOICE2)) 
		{
	        	WSACleanup();
				return 0; /* desired Winsock version was not available */
		}
		_haveInitializedWinsock = 1;
	}

	return 1;
}
int releaseWinsockIfNecessary(void)
{
	WSACleanup();
	return 1;
}

#else
#define SOCKET int
#define  INVALID_SOCKET -1
int initializeWinsockIfNecessary(void) { return 1; }
int releaseWinsockIfNecessary(void){ return 1; }
#endif
int makeSocketNonBlocking(int sock) {
#if defined(__WIN32__) || defined(_WIN32)
	unsigned long arg = 1;
	return ioctlsocket(sock, FIONBIO, &arg) == 0;
#else
	int curFlags = fcntl(sock, F_GETFL, 0);
	return fcntl(sock, F_SETFL, curFlags|O_NONBLOCK) >= 0;
#endif
}

int makeSocketBlocking(int sock) {
#if defined(__WIN32__) || defined(_WIN32)
	unsigned long arg = 0;
	return ioctlsocket(sock, FIONBIO, &arg) == 0;
#else
	int curFlags = fcntl(sock, F_GETFL, 0);
	return fcntl(sock, F_SETFL, curFlags&(~O_NONBLOCK)) >= 0;
#endif
}
int getLastError()
{
#if defined(__WIN32__) || defined(_WIN32)
	return WSAGetLastError();
#else
	return errno;
#endif
}

int sendCmd(SOCKET s,const char cmd[])
{
	int len = strlen(cmd)+1;
	char buff[8] = {0};
	buff[0] = 'V';
	buff[1] = 'Z';
	int nlen = htonl(len);
	memcpy(&buff[4], &nlen, 4);
	
	if (send(s, buff,8,0) == 8)
	{
		return send(s,cmd,len,0);
	}
	else
	{
		return 0;
	}	
}

int sendKeepAlive(SOCKET s)
{
	char buff[8]={0};
	buff[0] = 'V';
	buff[1] = 'Z';
	buff[2] = 1;
	return send(s, buff, 8, 0);
}

int recvPacketSize(SOCKET s)
{
	char header[8] = {0};
	int len = 0;
	//确保收到8个字节

	len = recv(s, header, 8 , 0);
	if ( SOCKET_ERROR == len)
	{
		return -1;
	}


	if ('V' == header[0] && 'Z' == header[1])
	{
		int toRecvLen = 0;
		memcpy(&toRecvLen, &header[4], 4);
		return header[2]==1? 0: htonl(toRecvLen);
	}
	return -1;
}

int recvPacket(SOCKET s,char buff[],int len)
{
	int toRecvLen = recvPacketSize(s);
	
	if (toRecvLen > 0)
	{
		return recv(s,buff,toRecvLen>len?len:toRecvLen,0);
	}
	else if(toRecvLen < 0)
	{
		//socket error
		return -1;
	}
	return 0;

}
int getOfflineImageById(SOCKET s,int id)
{
	char ctl[256];

	sprintf(ctl, "{"
		"\"cmd\" : \"get_offline_image\","
		"\"id\" : %d"
		"}", id);

	if ( sendCmd(s, ctl) == strlen(ctl)+1)
	{
		return 0;
	}
	else
	{
		return -1;
	}
}
void saveImage(char buff[], int size,char fileName[260])
{	
	FILE* file;
	char secFilename[260];
	sprintf(secFilename,"%s%ld.jpg", fileName,time(NULL));
	file= fopen(secFilename ,"wb");

	
	if (NULL == file)
	{
		perror("create image file failed!");
		return ;
	}
	if (fwrite(buff,1,size,file) == size)
	{
		printf("save image file: %s!\n", secFilename);
	}
	
	fclose(file);
}
#ifdef _USE_JSON_LIB
void saveForTest(char recvBuffer[],int len)
{
	char lpr[64] = {0};
	time_t sec = 0;

	try {
		Json::Reader reader;
		Json::Value root;
		bool parsingSuccessful = reader.parse(recvBuffer, root);
		if (!parsingSuccessful) {
			printf("Failed to parse file: \n%s\n",				
				reader.getFormattedErrorMessages().c_str());			
		}
		strcpy(lpr, root["PlateResult"]["license"].asCString());
		sec = root["PlateResult"]["timeStamp"]["Timeval"]["sec"].asUInt();
	}
	catch (const std::exception &e) {
		printf("Unhandled exception:\n%s\n", e.what());
	}	
	if (lpr[0] == 0)
	{
		strcpy(lpr,"none");
	}
	if (sec == 0)
	{
		strcat(lpr," - no time");
		sec = time(NULL);
	}

	char imageFileName[_MAX_FNAME];
	struct tm *p = localtime(&sec);
	sprintf(imageFileName,"%s - [%.4d_%.2d_%.2d %.2d_%.2d_%.2d]", lpr,
		(1900+p->tm_year), (1+p->tm_mon), p->tm_mday,
		p->tm_hour, p->tm_min, p->tm_sec);

	//save to txt
	const char lprFileName[] = "ivsresult.txt";
	FILE* file = fopen(lprFileName ,"at+");
	if (NULL == file)
	{
		perror("create txt file failed!");
		return ;
	}
	int size= strlen(imageFileName);
	if (fwrite(imageFileName, 1, size, file) == size)
	{
		//printf("write file ok!\n");
	}
	fputc('\n', file);
	fclose(file);
	//save image
	strcat(imageFileName,".jpg");
	file = fopen(imageFileName ,"wb");
	if (NULL == file)
	{
		perror("create image file failed!");
		return ;
	}
	int pos = strlen(recvBuffer) + 1;
	size= len - pos;
	if (fwrite(&recvBuffer[pos], 1, size, file) == size)
	{
		//printf("write file ok!\n");
	}
	printf("%s \n", imageFileName);
	fclose(file);
}
#endif
void parseBinIVSResult(char *pBuffer, unsigned len, char fileName[])
{
	BLOCK_HEADER* pHeader = (BLOCK_HEADER*)pBuffer;
	if (pHeader->length == 0)
	{
		//没有识别结果
	}
	else
	{
		//识别结果
		if (pHeader->type == BLOCK_TYPE_BIN_RESULT &&
			pHeader->length == sizeof(TH_PlateResult))
		{
			//二进制格式
			TH_PlateResult *pResult= (TH_PlateResult *)(&pBuffer[sizeof(BLOCK_HEADER)]);
			printf("licence:%s,id:%d\n", pResult->license, pResult->uId);

			//判断有没有图片块头
			int blockOffset = pHeader->length + sizeof(BLOCK_HEADER);
			if ( blockOffset + sizeof(BLOCK_HEADER) < len)
			{
				BLOCK_HEADER* pImageHeader = (BLOCK_HEADER*)(&pBuffer[blockOffset]);
				blockOffset += sizeof(BLOCK_HEADER);
				//判断图片接收是否完整
				if (pImageHeader->type == BLOCK_TYPE_IMAGE_DATA &&
					blockOffset + pImageHeader->length <= len)
				{
					saveImage(&pBuffer[blockOffset], pImageHeader->length, "big");
                    blockOffset += pImageHeader->length;
                    pImageHeader = (BLOCK_HEADER*)(&pBuffer[blockOffset]);
                    blockOffset += sizeof(BLOCK_HEADER);
				}

                if (pImageHeader->type == BLOCK_TYPE_IMAGE_FRAGMENT_DATA &&
                    blockOffset + pImageHeader->length <= len)
                {
                    saveImage(&pBuffer[blockOffset], pImageHeader->length, "small");
                }
			}
		}		
		else
		{
			//其它格式
		}

	}
}
int doAsyncRecvPacket(SOCKET client,int packetSize,char fileName[])
{
	char* recvBuffer = new char[packetSize];
	int totleRecvSize = 0;
	int recvSize = 0;
	while(totleRecvSize < packetSize)
	{
		recvSize = recv(client, &recvBuffer[totleRecvSize], 
			packetSize - totleRecvSize, 0);
		if (recvSize > 0)
		{
			totleRecvSize += recvSize;
		}
		else
		{
			printf("recv error code :%d\n", getLastError());
			delete recvBuffer;
			return 0;
		}						
	}

	if(totleRecvSize == packetSize)
	{
		//接收到完整的数据包后做格式化解析处理
#ifdef _USE_JSONCPP_LIB			
		saveForTest(recvBuffer, packetSize);
#else
		//接收的数据如果是二进制识别结果，会带有一个头BLOCK_HEADER
		if (totleRecvSize > sizeof(BLOCK_HEADER))
		{
			BLOCK_HEADER* pHeader = (BLOCK_HEADER*)recvBuffer;
			if (pHeader->magic[0] == 'I' && pHeader->magic[1] == 'R')
			{
                //脱机的响应
				sendCmd(client, offLineNotify);
				parseBinIVSResult(recvBuffer, totleRecvSize, fileName);
			}
			else
			{
				//其它json格式数据，建议使用JSON库进行解析
				char *pJson = (char *)(&recvBuffer[0]);
				printf("ivs result:\n%s",pJson);
				if (strstr(pJson, "\"cmd\":\"offline\"") != NULL)
				{
                    //注册脱机后，服务端的返回
					printf("%s\n",pJson);
					sendCmd(client, getOfflineRecord);
				}
				else if (strstr(pJson, "\"cmd\":\"offline_record\"") != NULL)
				{
                    //服务端返回的脱机记录列表，不包含图片
					printf("%s\n",pJson);
					int pos = strlen(pJson);

					pos++;
					while(pos < packetSize)
					{
						TH_PlateResult *pResult= (TH_PlateResult *)(&recvBuffer[pos]);
						unsigned id = pResult->uId;
						printf("id:%d,licence:%s\n",id,pResult->license);
						
						pos += sizeof(TH_PlateResult);
                        //如果需要，可以取图片
                        //getOfflineImageById(client, id);
					}					
				}
				else if (strstr(pJson, "\"cmd\":\"get_offline_image\"") != NULL)
				{
                    //服务端按ID返回记录的图片
					int ivsLen = strlen(pJson) + 1;
					printf("%s\n",pJson);
					if (ivsLen < totleRecvSize)
					{
						saveImage(&recvBuffer[ivsLen], totleRecvSize - ivsLen, NULL);
					}
				}
				else if (strstr(pJson, "\"cmd\":\"ivs_result\"") != NULL)
				{
                    //脱机的响应
					sendCmd(client, offLineNotify);
					//判断有没有图片
					int ivsLen = strlen(pJson) + 1;
                    char bigImageTag[] = "\"fullImgSize\":";
                    char smallImageTag[] = "\"clipImgSize\":";
                    char *pImageSize = NULL;
                    int bigImageSize = 0, smallImageSize = 0;
                    if ((pImageSize =strstr(pJson, bigImageTag)) != NULL)
                    {
                        bigImageSize = atoi(&pImageSize[strlen(bigImageTag)]);
                    }
                    if ((pImageSize =strstr(pJson, smallImageTag)) != NULL)
                    {
                        smallImageSize = atoi(&pImageSize[strlen(smallImageTag)]);
                    }

					if (ivsLen + bigImageSize + smallImageSize <= totleRecvSize)
					{
                        if (bigImageSize > 0)
                        {
                            saveImage(&recvBuffer[ivsLen], bigImageSize, "big");
                        }
                        if (smallImageSize > 0)
                        {
                            saveImage(&recvBuffer[ivsLen + bigImageSize], smallImageSize, "small");
                        }
					}
				}
                else
                {
                    printf("un-handled msg!\n");
                }
			}
		}
#endif			
	}
	delete recvBuffer;
	return totleRecvSize;
}
void recvIVSResult(SOCKET s,char ivsResult[],int len, char fileName[])
{

	int packetSize = recvPacketSize(s);

	if (packetSize>0)
	{
		doAsyncRecvPacket(s, packetSize,  fileName);
	}

}
//配置识别结果的传输格式及是否要接收图片
//fmt = 0 传输二进制结构体,fmt = 1 传输编码后的json字符串
//image，0为传输大图片，1为要传输小图片，2为大小图片都传，3不带图片
//bEnable, 0表示不主动推送，1表示主动推送
int cfgTransferFmt(SOCKET s, int bEnable, int fmt, int image)
{
	char ctl[256];
	
	sprintf(ctl, "{"
		"\"cmd\" : \"ivsresult\","
		"\"enable\" : %s,"
		"\"format\" : \"%s\","
		"\"image\" : %s,"
        "\"image_type\" : %d"
		"}", 
		 bEnable?"true":"false",
		fmt==0?"bin":"json", 
		image != IMAGE_TYPE_DISABLE?"true":"false",image);
	
	if ( sendCmd(s, ctl) == strlen(ctl)+1)
	{
		return 0;
	}
	else
	{
		return -1;
	}
}
#define CMD_TRIGGER "trigger"
#define MAKE_JSON(cmd) "{cmd:\""##cmd"\"}"
int sendIOCtl(SOCKET s,int ioNum, int value)
{
	char json[256];
	int delay = 500;
	sprintf(json, "{"
		"\"cmd\" : \"ioctl\","
		"\"io\" : %d,"
		"\"value\" : %d,"
		"\"delay\" : %d"
		"}", 
		ioNum, value, delay);

	if ( sendCmd(s, json) == strlen(json)+1)
	{
		return 0;
	}
	else
	{
		return -1;
	}
}
SOCKET connetServer(char *ip, int port, int block)
{
	SOCKET client;
	struct sockaddr_in addr;
	unsigned long ip_addr = inet_addr(ip);

	if (INADDR_NONE == ip_addr)
	{
		printf("input right ip,eg:192.168.1.22\n");
		return INVALID_SOCKET;
	}
	if(initializeWinsockIfNecessary() == 0)
	{
		printf("WSAStartup error!/n");
		return INVALID_SOCKET;
	}
	client=socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
	if(INVALID_SOCKET == client)
	{
		printf("create socket error/n");
		releaseWinsockIfNecessary();
		return INVALID_SOCKET;
	}

	if (block == TCP_UNBLOCK)
	{
		makeSocketNonBlocking(client);
	}
	else
	{
		makeSocketBlocking(client);
	}	

	addr.sin_family=AF_INET;
	addr.sin_port=htons(port);
	addr.sin_addr.s_addr= ip_addr;
	if(connect(client,(struct sockaddr *)&addr,sizeof(addr))!=0)
	{
		printf("connect error/n");
		closeSocket(client);
		releaseWinsockIfNecessary();
		return INVALID_SOCKET;
	}
	return client;
}
void socketLoop(SOCKET client)
{	
	fd_set readSet;
	struct timeval tv = {15, 0}; 
	int ret;
	
	//定时发送心跳包，推荐使用系统的定时器功能,这里采用简单计时的方法
	time_t time_pre = time(NULL);
	time_t time_now = 0;

	sendKeepAlive(client);
	while(1)
	{
		//wait for image upload
		tv.tv_sec = 15;
		FD_ZERO(&readSet);
		FD_SET(client, &readSet);

		ret = select(client+1,&readSet,NULL,NULL,&tv);
		printf("select ret:%d\n",ret);
		if(ret > 0)
		{			
			if (FD_ISSET(client, &readSet))
			{
				int packetSize = recvPacketSize(client);
				printf("recvPacketSize:%d\n",packetSize);
				if (packetSize>0)
				{
					//接收数据处理，目前主要是识别结果的推送
					//可能包含其它结果，如获取到的序列号
					//具体内容根据接收到的json数据解析判断

					doAsyncRecvPacket(client, packetSize, NULL);
				}
				else if (packetSize == 0)
				{
					//接收到心跳包的回应
					printf("keep alive responsed\n");
				}
				else
				{
					//recv函数返回SOCKET_ERROR ，网络错误
					printf("recv error code :%d\n", getLastError());
					break;
				}

				// sendIOCtl(client,IO_OUT1, IO_OUT_SQUARE_HIGH);

			}
		}
		else if (ret == 0)
		{
			//select timeout
		}
		else if (ret == SOCKET_ERROR)
		{
			printf("recv error code :%d\n",getLastError());
			break;
		}
		//需要定时发送心跳包
		time_now = time(NULL);
		if (time_now - time_pre > tv.tv_sec)
		{
			// sendIOCtl(client,IO_OUT1, IO_OUT_SQUARE_HIGH);

			//timeout
			if (sendKeepAlive(client) != SOCKET_ERROR )
			{
				printf("sendKeepAlive\n");
				time_pre = time_now;
				continue;
			}
			else
			{
				// error 
				break;
			}			
		}
	}
}

int main(int argc, char* argv[])
{
	SOCKET client;

	client = connetServer(DEFAULT_IP, DEFAULT_PORT, TCP_BLOCK);
	if (INVALID_SOCKET ==client)
	{
		return 0;
	}
	char buff[256] = {0};
	strcpy(buff, getsn);
	sendCmd(client,getsn);
	recvPacket(client,buff, 256);
	printf("value:%s\n",buff);

#ifdef _TEST_GET_IVS
	char ivsResult[1024];
	char fileName[_MAX_FNAME];
	sendCmd(client,getivsresult);
	recvIVSResult(client,ivsResult, 1024,fileName);
#endif

	if (cfgTransferFmt(client, ENABLE_PUSH, FORMAT_JSON, IMAGE_TYPE_BOTH) < 0)
	{
		printf("cfg error!\n");
	}
	
	printf("ready to recv image!\n");
	
	sendCmd(client, triggerIVSResult);
	// sendCmd(client, triggerIVSResult);
	sendIOCtl(client,IO_OUT1, IO_OUT_SQUARE_HIGH);
	
	
	socketLoop(client);

	printf("recv error code :%d\n", getLastError());
	closeSocket(client);
	releaseWinsockIfNecessary();
	getchar();
	return 0;
}