#include "MultiRecv.h"
#include <iostream>
#include <stdio.h>
#include <string.h>


namespace vzsdk {
#define WORKING_BUFFER_SIZE 15000
#define MAX_TRIES 3
#define TIMEOUT_TIMES 4

#define MALLOC(x) HeapAlloc(GetProcessHeap(), 0, (x))
#define FREE(x) HeapFree(GetProcessHeap(), 0, (x))

// The multicast udp max packet size
// The MTU must not be confused with the minimum datagram size that all hosts
// must be prepared to accpet, which has a value of 576 bytes for IPv4 and
// of 1280 bytes for IPv6
// see more infor: http://en.wikipedia.org/wiki/Maximum_transmission_unit
#define MAX_PACKET_SIZE 1024

MultiRecv::MultiRecv() {
    param_.callback = NULL;
    param_.user_data = NULL;
    sockfd_ = -1;
}
MultiRecv::~MultiRecv() {
    StopFindDevice();
}

void MultiRecv::StopFindDevice() {
    if (!BasicThread::IsRun()) {
        return;
    }

    BasicThread::Stop();

    if (sockfd_ != -1) {

#if WIN32
        closesocket(sockfd_);
#else
        close(sockfd_);
#endif
        sockfd_ = -1;
    }

}

bool MultiRecv::StartFindDevice(const char *multicast_addr, unsigned short multicast_port, const char *send_data,
                                MultiRecvCallback callback, void* user_data) {
    if (BasicThread::IsRun()) {
        StopFindDevice();
    }
    param_.send_text = send_data;
    param_.multicast_addr = multicast_addr;
    param_.multicast_port = multicast_port;
    param_.callback = callback;
    param_.user_data = user_data;
    if(!BasicThread::Start()) {
        return false;
    }
    return true;
}
bool MultiRecv::CreateMultiSocket() {

    int                 socklen;

    // Create Udp socket that used to recv data
    sockfd_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd_ < 0) {
        std::cerr << "socket creating err in udptalk" << std::endl;
        return false;
    }
    int enable = 1;
    if (setsockopt(sockfd_, SOL_SOCKET, SO_REUSEADDR,
                   (const char*)&enable, sizeof(int)) < 0) {
        std::cerr << "SO_REUSEADDR error" << std::endl;
        return false;
    }

    // Init the multicast address
    socklen = sizeof(struct sockaddr_in);
    memset(&peeraddr_, 0, socklen);
    peeraddr_.sin_family = AF_INET;
    peeraddr_.sin_port = htons(param_.multicast_port);
    peeraddr_.sin_addr.s_addr = INADDR_ANY;//inet_addr("0.0.0.0");//

    // Bind INADDR_ANY
    if (bind(sockfd_, (struct sockaddr *) &peeraddr_,
             sizeof(struct sockaddr_in)) == -1) {
        std::cerr << "Bind error ... ..." << std::endl;
        return false;
    }

    ChangeMulticastMembership(sockfd_, param_.multicast_addr.c_str());


    return true;
}

bool MultiRecv::SendData(const char*data, int data_len) {
    bool res = false;
    if (data && data_len > 0 && sockfd_ >0) {
        int len = sendto(sockfd_,
                         data,
                         data_len,
                         0,
                         (struct sockaddr *)&sendAddr_,
                         sizeof(sendAddr_));

        if (-1 != len) {
            res = true;
        }
    }

    return res;
}

void MultiRecv::Run( ) {

    char                recmsg[MAX_PACKET_SIZE]= {0};

    fd_set read_fds;
    timeval timeout;
    time_t start_time, end_time;
    int                 socklen;
    int        n;
    int                 tempsockfd = sockfd_;
    std::string deviceip;

    // Create Udp socket that used to recv data
    sockfd_ = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd_ < 0) {
        std::cerr << "socket creating err in udptalk" << std::endl;
        return  ;
    }
    int enable = 1;
    if (setsockopt(sockfd_, SOL_SOCKET, SO_REUSEADDR,
                   (const char*)&enable, sizeof(int)) < 0) {
        std::cerr << "SO_REUSEADDR error" << std::endl;
        return  ;
    }

    // Init the multicast address
    socklen = sizeof(struct sockaddr_in);
    memset(&peeraddr_, 0, socklen);
    peeraddr_.sin_family = AF_INET;
    peeraddr_.sin_port = htons(param_.multicast_port);
    peeraddr_.sin_addr.s_addr = INADDR_ANY;//inet_addr("192.168.1.118");//


    // Bind INADDR_ANY
    if (bind(sockfd_, (struct sockaddr *) &peeraddr_,
             sizeof(struct sockaddr_in)) == -1) {
        std::cerr << "Bind error ... ..." << std::endl;
        return  ;
    }

    memset(&sendAddr_, 0, sizeof(sendAddr_));
    sendAddr_.sin_family = AF_INET;
    sendAddr_.sin_port = htons(param_.multicast_port);

    sendAddr_.sin_addr.s_addr = inet_addr(param_.multicast_addr.c_str());

    ChangeMulticastMembership(sockfd_, param_.multicast_addr.c_str());

    SendData(param_.send_text.c_str(),param_.send_text.length());

    // Init start time
    time(&start_time);

    while (IsRun()) {
        timeout.tv_sec = TIMEOUT_TIMES;
        timeout.tv_usec = 0;
        FD_ZERO(&read_fds);
        FD_SET(sockfd_, &read_fds);

        int res = select(sockfd_+1, &read_fds, NULL, NULL,&timeout);
        if (res == -1) {
            printf("select call failed with error\n");
            return;
        } else {
            if (FD_ISSET(sockfd_, &read_fds)) {
                n = recvfrom(sockfd_, recmsg, MAX_PACKET_SIZE, 0,
                             (struct sockaddr *) &peeraddr_, (socklen_t*)&socklen);
                if (n < 0) {
                    return;
                } else {
                    if (param_.callback) {
                        deviceip = inet_ntoa(peeraddr_.sin_addr);
                        param_.callback(deviceip.c_str(), recmsg, n, param_.user_data);
                    }
                    if (!param_.send_text.empty()) {
                        //发送请求消息
                        SendData(param_.send_text.c_str(),param_.send_text.length());
                    }

                }
            }
            time(&end_time);
            if (end_time - start_time > TIMEOUT_TIMES * 2) {
                start_time = end_time;

                ChangeMulticastMembership(sockfd_, param_.multicast_addr.c_str());
            }


        }
    }

    if (sockfd_ |= -1) {
#if WIN32
        closesocket(sockfd_);
#else
        close(sockfd_);

#endif
        sockfd_ = -1;
    }
}

int MultiRecv::ChangeMulticastMembership(int sockfd, const char* multicast_addr) {

    struct ip_mreq mreq;
    memset(&mreq, 0, sizeof(struct ip_mreq));
    std::vector<NetworkInterfaceState> net_inter;

    UpdateNetworkInterface(net_inter);

    for (std::size_t i = 0; i < net_inter.size(); i++) {
        // Find network interface
        bool is_found = false;
        for (std::size_t j = 0; j < network_interfaces_.size(); j++) {
            if (network_interfaces_[j].adapter_name == net_inter[i].adapter_name
                    && network_interfaces_[j].ip_addr == net_inter[i].ip_addr
                    && network_interfaces_[j].state == INTERFACE_STATE_ADD) {
                is_found = true;
                break;
            }
        }
        if (!is_found) {
            mreq.imr_interface.s_addr = net_inter[i].ip_addr;
            mreq.imr_multiaddr.s_addr = inet_addr(multicast_addr);
            if (setsockopt(sockfd, IPPROTO_IP, IP_ADD_MEMBERSHIP,
                           (const char *)&mreq, sizeof(struct ip_mreq)) == -1) {
                net_inter[i].state = INTERFACE_STATE_ERROR;
            } else {
                net_inter[i].state = INTERFACE_STATE_ADD;
            }
        }
    }
    network_interfaces_ = net_inter;
    return 0;
}

int MultiRecv::UpdateNetworkInterface(std::vector<NetworkInterfaceState>& net_inter) {
#ifdef WIN32
    /* Declare and initialize variables */
    DWORD dwSize = 0;
    DWORD dwRetVal = 0;

    unsigned int i = 0;

    // Set the flags to pass to GetAdaptersAddresses
    ULONG flags = GAA_FLAG_INCLUDE_PREFIX;

    // default to unspecified address family (both)
    ULONG family = AF_INET;

    LPVOID lpMsgBuf = NULL;

    PIP_ADAPTER_ADDRESSES pAddresses = NULL;
    ULONG outBufLen = WORKING_BUFFER_SIZE;
    ULONG Iterations = 0;

    PIP_ADAPTER_ADDRESSES pCurrAddresses = NULL;
    PIP_ADAPTER_UNICAST_ADDRESS pUnicast = NULL;
    PIP_ADAPTER_ANYCAST_ADDRESS pAnycast = NULL;
    PIP_ADAPTER_MULTICAST_ADDRESS pMulticast = NULL;
    IP_ADAPTER_DNS_SERVER_ADDRESS *pDnServer = NULL;
    IP_ADAPTER_PREFIX *pPrefix = NULL;
    // char temp_ip[128];

    do {

        pAddresses = (IP_ADAPTER_ADDRESSES *)MALLOC(outBufLen);
        if (pAddresses == NULL) {
            printf
            ("Memory allocation failed for IP_ADAPTER_ADDRESSES struct\n");
            return 1;
        }

        dwRetVal =
            GetAdaptersAddresses(family, flags, NULL, pAddresses, &outBufLen);

        if (dwRetVal == ERROR_BUFFER_OVERFLOW) {
            FREE(pAddresses);
            pAddresses = NULL;
        } else {
            break;
        }

        Iterations++;

    } while ((dwRetVal == ERROR_BUFFER_OVERFLOW) && (Iterations < MAX_TRIES));

    // -------------------------------------------------------------------------
    if (dwRetVal == NO_ERROR) {
        // If successful, output some information from the data we received
        pCurrAddresses = pAddresses;
        while (pCurrAddresses) {
            //printf("\tAdapter name: %s\n", pCurrAddresses->AdapterName);
            pUnicast = pCurrAddresses->FirstUnicastAddress;
            if (pUnicast != NULL) {
                for (i = 0; pUnicast != NULL; i++) {
                    if (pUnicast->Address.lpSockaddr->sa_family == AF_INET) {
                        NetworkInterfaceState nif;
                        sockaddr_in* v4_addr =
                            reinterpret_cast<sockaddr_in*>(pUnicast->Address.lpSockaddr);
                        //printf("\tIpv4 pUnicast : %s\n", hexToCharIP(temp_ip, v4_addr->sin_addr));
                        nif.ip_addr = v4_addr->sin_addr.S_un.S_addr;
                        nif.adapter_name = pCurrAddresses->AdapterName;
                        nif.state = INTERFACE_STATE_NONE;
                        net_inter.push_back(nif);
                    }
                    pUnicast = pUnicast->Next;
                }
            }
            pCurrAddresses = pCurrAddresses->Next;
        }
    } else {
        if (dwRetVal != ERROR_NO_DATA) {
            if (FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER |
                              FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                              NULL, dwRetVal, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                              // Default language
                              (LPTSTR)& lpMsgBuf, 0, NULL)) {
                std::cerr << "Error " << lpMsgBuf << std::endl;
                LocalFree(lpMsgBuf);
                if (pAddresses)
                    FREE(pAddresses);
                return 1;
            }
        }
    }

    if (pAddresses) {
        FREE(pAddresses);
    }
#else
NetworkInterfaceState nif;

nif.ip_addr = htonl(INADDR_ANY);
nif.adapter_name = "localhost";
nif.state = INTERFACE_STATE_NONE;
net_inter.push_back(nif); 
#endif

    return 0;
}
}
