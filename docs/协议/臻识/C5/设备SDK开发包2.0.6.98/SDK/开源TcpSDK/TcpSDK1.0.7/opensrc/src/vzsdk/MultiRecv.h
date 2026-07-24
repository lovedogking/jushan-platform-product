#ifndef DEVICE_FOUND_H_
#define DEVICE_FOUND_H_

#include <string>
#include <vector>

#include <set>
#include <map>

#include "posix_thread.h"
#include "vzsdk/vzlprtcpsdk.h"

#if WIN32
#define _WINSOCKAPI_
#include <WinSock2.h>
#include <Ws2tcpip.h>
#include <iphlpapi.h>
//#include<windows.h>
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
#endif

namespace vzsdk {
typedef void(*MultiRecvCallback)(
    const char * deviceip,
    const char* /*data*/,
    int   /*size*/,
    void* /*user_data*/
);

struct MultiRecvParam {
    std::string multicast_addr;
    unsigned short multicast_port;
    std::string  send_text;
    MultiRecvCallback callback;
    void* user_data;
};

class MultiRecv :public BasicThread {
  public:
    MultiRecv();
    virtual ~MultiRecv();
    bool StartFindDevice(const char *multicast_addr, unsigned short multicast_port, const char *send_data,
                         MultiRecvCallback callback, void* user_data);

    void StopFindDevice();

    bool SendData(const char*data, int data_len);

  private:

    bool CreateMultiSocket();

    virtual void Run();
    enum InterfaceState {
        INTERFACE_STATE_NONE,
        INTERFACE_STATE_ADD,
        INTERFACE_STATE_ERROR
    };
    struct NetworkInterfaceState {
        std::string adapter_name;
        unsigned long ip_addr;
        InterfaceState state;
    };
    int UpdateNetworkInterface(std::vector<NetworkInterfaceState>& net_inter);
    int ChangeMulticastMembership(int sockfd, const char* multicast_addr);
    std::vector<NetworkInterfaceState> network_interfaces_;
    std::set<std::string> ip_address_;
    MultiRecvParam      param_;
    struct sockaddr_in  peeraddr_;
    struct sockaddr_in  sendAddr_;
    int                 sockfd_;

};
}

#endif // DEVICE_FOUND_H_