#include "changedevicenetwork.h"


#include <stdio.h>
#include <stdlib.h>
#include <vector>
#include <string>

#ifdef WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
#else 
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <net/if_arp.h>
#include <netdb.h>
#include <unistd.h>
#include <string.h>
#endif
 
#define MAX_PACKET_SIZE 20


using namespace std; 

void fetchIPAddress(vector<string> &vecIP)  
{  
	char host_name[225] = {0};  
	if(gethostname(host_name,sizeof(host_name))==-1)  
	{  
		return;
	}  

	struct hostent *phe=gethostbyname(host_name);  
	if(phe==0)  
	{  
		return;
	}  

	int i = 0;
	while( phe->h_addr_list[i] != NULL )
	{
		char *ip = inet_ntoa(*(struct in_addr *)phe->h_addr_list[i]);
		vecIP.push_back(ip);

		i++;

	}  
} 

void getIP(vector<string> &vecIP)
{  
#ifdef WIN32
	WSAData wsaData;
	if (WSAStartup(MAKEWORD(1, 1), &wsaData) != 0)
	{
		return;
	}
#endif
	

	fetchIPAddress(vecIP);  
#ifdef WIN32
	WSACleanup();
#endif

}  



int ChangeDeviceNetwork(unsigned long dsn_high,
  unsigned long dsn_low,
  const char* multicast_addr,
  unsigned short multicast_port,
  unsigned long new_ipaddr,
  unsigned long new_gateway,
  unsigned long new_ipmask){

  struct sockaddr_in peeraddr, myaddr;
  int sockfd;
  char send_buffer[MAX_PACKET_SIZE];
  unsigned int socklen;
  int res = -1;

  vector<string> vecIP;
  getIP( vecIP ); 

  int nSize = vecIP.size( );
  for( int i = 0; i < nSize; i++ )
  {
	  memset(send_buffer, 0, MAX_PACKET_SIZE);

	  // Create Socket
	  sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);

	  if (sockfd == -1) {
		  printf("socket creating error\n");
		  return CHANGE_FAILURE;
	  }
	  socklen = sizeof(struct sockaddr_in);

	  int ttl = 255;
	  res = setsockopt(sockfd, IPPROTO_IP, IP_MULTICAST_TTL, 
		  (const char *)&ttl, sizeof(ttl));
	  if (res == -1){
		  printf("setsockopt IP_MULTICAST_LOOP fall\n");
		  return 1;
	  }

	  u_char loop = 0;
	  res = setsockopt(sockfd, IPPROTO_IP, IP_MULTICAST_LOOP, 
		  (const char *)&loop, sizeof(loop));
	  if (res == -1){
		  printf("setsockopt IP_MULTICAST_LOOP fall\n");
		  return CHANGE_FAILURE;
	  }

	  // Setting multicast addr and port
	  memset(&peeraddr, 0, socklen);
	  peeraddr.sin_family = AF_INET;
	  peeraddr.sin_port = htons(multicast_port);
	  peeraddr.sin_addr.s_addr = inet_addr(multicast_addr);

	  // Setting local bind addr and port
//	  memset(&myaddr, 0, socklen);
//	  myaddr.sin_family = AF_INET;
//	  myaddr.sin_port = htons(23456);
//	  
//	  myaddr.sin_addr.s_addr = inet_addr(vecIP[i].c_str());
//	  
//	  // Bind at local interface
//	  if (bind(sockfd, (struct sockaddr *) &myaddr, sizeof(struct sockaddr_in)) == -1) {
//		  printf("Bind error\n");
//		  return CHANGE_FAILURE;
//	  }

#ifdef _DEBUG
//	  socklen_t len = 0;
//	  len = sizeof(struct sockaddr_in);
//	  getsockname(sockfd, (struct sockaddr *)&myaddr, &len);

//	  printf("addr:%s\n", inet_ntoa(myaddr.sin_addr));
//	  printf("port:%d\n", myaddr.sin_port);
#endif
	  dsn_high = htonl(dsn_high);
	  dsn_low = htonl(dsn_low);
	  new_ipaddr = htonl(new_ipaddr);
	  new_gateway = htonl(new_gateway);
	  new_ipmask = htonl(new_ipmask);
	  memcpy(send_buffer, (const void *)(&dsn_high), 4);
	  memcpy(send_buffer + 4, (const void *)(&dsn_low), 4);
	  memcpy(send_buffer + 8, (const void *)(&new_ipaddr), 4);
	  memcpy(send_buffer + 12, (const void *)(&new_gateway), 4);
	  memcpy(send_buffer + 16, (const void *)(&new_ipmask), 4);

	  /* ·¢ËÍÏûÏ¢ */
	  if (sendto(sockfd, send_buffer, MAX_PACKET_SIZE, 0, 
		  (struct sockaddr *) &peeraddr, sizeof(struct sockaddr_in)) < 0){
			  printf("sendto error!\n");
			  return CHANGE_FAILURE;
	  }
#ifdef _DEBUG
	  printf("send ok\n");
#endif
	  
#if WIN32
	  closesocket(sockfd);
#else
    close(sockfd);
#endif
  }

  
  return CHANGE_SUCCEED;
}

const char* GetRecommendSubnetMask(const char* ip_addr){

  unsigned long nip = inet_addr(ip_addr);
  nip = htonl(nip);
  if (nip == INADDR_NONE){
    return "255.255.255.0";
  }
  // Varify Class A IP addr
  if ((nip & 0X80000000) == 0X00000000){
    return "255.0.0.0";
  }
  if ((nip & 0XC0000000) == 0X80000000){
    return "255.255.0.0";
  }
  if ((nip & 0XE0000000) == 0XC0000000){
    return "255.255.255.0";
  }
  return "255.255.255.0";
}

int VerifyIpaddrGatawayCorrect(const char* ip_addr,
  const char* gateway, const char* net_mask){

  unsigned long nip = inet_addr(ip_addr);
  unsigned long ngateway = inet_addr(gateway);
  unsigned long nnetmarsk = inet_addr(net_mask);

  if (nip == INADDR_NONE || ngateway == INADDR_NONE || nnetmarsk == INADDR_NONE){
    return CHANGE_FAILURE;
  }

  if ((nip & nnetmarsk) == (ngateway & nnetmarsk)){
    return CHANGE_SUCCEED;
  }

  return CHANGE_FAILURE;
}