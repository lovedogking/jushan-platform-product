#include "LprDeviceFinder.h"
#include <string.h>

#include "stdio.h"

typedef struct SPacketHead {
    char Signature[2];
    uint16 HeaderChksum;
    uint16 version;
    uint16 type;
    uint32 information;
    uint32 DstSerialHI;
    uint32 DstSerialLO;
    uint32 SrcSerialHI;
    uint32 SrcSerialLO;
    uint32 sequence_major;
    uint32 sequence_minor;
    uint32 timestamp;
    uint16 seqnum;
    uint16 sortnum;
    uint16 length;
    uint16 chksum;
} SPacketHead;

#define  	VS_SIGN				"VS"
#define  	VZ_SIGN				"VZ"

#define 	VERSION_MAJOR			0x0100
#define 	VERSION_MINOR			0x0002

#define  DATAASSIGN_NET_TCP			0x01       //TCP
#define  DATAASSIGN_NET_UDP			0x02       //UDP
#define  DATAASSIGN_NET_MCA		        0x03       //MULTI CAST

#define	        CURRENT_VERSION			(VERSION_MAJOR|VERSION_MINOR)

#define OEM_INFO_SIZE 7
#define HW_VERSION_SIZE 4
typedef struct fs_info_ex {
    char reserved;
    unsigned char oem_info[OEM_INFO_SIZE];		///< 前3位厂商的编号，后4位厂商简称

    //硬件版本号,1001是老版，新版第2字节高4位表示支持最大通道路数，低4位表示最大算法通道数，
    //第3字节高4位表示支持模拟最大数，低4位表示支持高清最大通道数，最后一位表示hwflag
    unsigned char hw_version[HW_VERSION_SIZE];

    unsigned int hw_flag;						///< 硬件标识,新版改为保存HwType
} fs_info_ex_t;

typedef struct NetMulticastServerInfo {
    unsigned int  ipaddr;
    unsigned int  port;
    char  DeviceName[32];
    unsigned int ChannelNum;

    unsigned int device_type;
    fs_info_ex_t ex_hwi;
    unsigned int  gateway;
    unsigned int  netmask;
    char  reserved[4]; //前面用了20字节
} NetMulticastServerInfo;


#define	TYPE_NOTIFY_MAJOR			0x0500
// notify : 主机设备定时发送此信息表明主机设备存在
#define	TYPE_NOTIFY				(TYPE_NOTIFY_MAJOR+0x00)
// notify : client 定时发送此信息表明client存在
#define	TYPE_NOTIFY_CLIENT			(TYPE_NOTIFY_MAJOR+0x01)
// notify : setup 定时发送此信息表明setup存在
#define	TYPE_NOTIFY_SETUP			(TYPE_NOTIFY_MAJOR+0x02)
// notify : elemap 定时发送此信息表明elemap存在
#define	TYPE_NOTIFY_ELEMAP			(TYPE_NOTIFY_MAJOR+0x03)
// notify : playback 定时发送此信息表明playback存在
#define	TYPE_NOTIFY_PLAYBACK 		        (TYPE_NOTIFY_MAJOR+0x04)

// notify : 解码设备 定时发送此信息表明存在
#define	TYPE_NOTIFY_DECCLIENT 		        (TYPE_NOTIFY_MAJOR+0x05)

// notify : 数据中转服务器 定时发送此信息表明存在
#define	TYPE_NOTIFY_ROUTER 		        (TYPE_NOTIFY_MAJOR+0x06)
// notify : 主机设备的 notify数据，经过数据转发器后的notify数据
#define	TYPE_NOTIFY_RT2RT 		        (TYPE_NOTIFY_MAJOR+0x07)
// notify : TYPE_NOTIFY_ROUTER回应，互相注册
#define	TYPE_NOTIFY_ROUTER_RETURN 	        (TYPE_NOTIFY_MAJOR+0x08)
// notify : 主机设备定时组播此信息表明主机设备存在
#define	TYPE_NOTIFY_MULCA			(TYPE_NOTIFY_MAJOR+0x09)



#define VS_PORT					22099

#define VS_STREAM_MC				VS_PORT         //22099
#define VS_MSG_MC				(VS_PORT+1)     //22100
#define VS_MSG_PORT				(VS_PORT+2)     //22101

#define VS_MCASTADDR_VIDEO			"228.5.6.1"
#define VS_MCASTADDR_MSG			"228.5.6.2"

LprDeviceFinder::LprDeviceFinder() {
    callback_ = 0;
    user_data_ = NULL;
}
LprDeviceFinder::~LprDeviceFinder() {

}

bool  LprDeviceFinder::StartFindDevice(VZLPRC_FIND_DEVICE_CALLBACK callback, void *user_data) {
    callback_ = callback;
    user_data_ = user_data;
    return MultiRecv_.StartFindDevice(VS_MCASTADDR_MSG, VS_MSG_MC, "", &LprDeviceFinder::OnMultiRecvCallback, this);
}

void LprDeviceFinder::StopFindDevice() {
    MultiRecv_.StopFindDevice();

    callback_ = 0;
    user_data_ = NULL;
}

void  LprDeviceFinder::OnMultiRecvCallback(const char * deviceip,
        const char* buf, int   len, void* user_data) {
    LprDeviceFinder * finder = (LprDeviceFinder*)user_data;
    
    if (!finder) {
        return;
    }

    SPacketHead *pHead = (SPacketHead *)buf;
    //标志是否正确
    if (strcmp(pHead->Signature, "VS") != 0 && strcmp(pHead->Signature, "VZ") != 0) {
        
        return;
    }
    //版本是否正确
    if (pHead->version != CURRENT_VERSION) {

        return;
    }
    //长度是否正确
    if (pHead->length != len) {

        return;
    }
    if ((pHead->type == TYPE_NOTIFY)){
    } else if ((pHead->type == TYPE_NOTIFY_MULCA) && len == sizeof(SPacketHead) + sizeof(NetMulticastServerInfo)) {
        //设备在线信息
        NetMulticastServerInfo * devinfo = (NetMulticastServerInfo*)(buf + sizeof(SPacketHead));

        char buffer[20] = { 0 };

        if (finder->callback_ != NULL) {
            char * ipaddr = NULL;
            in_addr inaddr;

            char netmask[32] = { 0 };
            inaddr.s_addr = devinfo->netmask;
            ipaddr = inet_ntoa(inaddr);
            if (ipaddr != NULL) {
                strncpy(netmask, ipaddr, sizeof(netmask) - 1);
            }

            char gateway[32] = { 0 };
            inaddr.s_addr = devinfo->gateway;
            ipaddr = inet_ntoa(inaddr);
            if (ipaddr != NULL) {
                strncpy(gateway, ipaddr, sizeof(gateway) - 1);
            }

            if (memcmp(&devinfo->device_type, buffer, 20) == 0) {
                finder->callback_(devinfo->DeviceName, deviceip, devinfo->port, 0, pHead->SrcSerialLO, pHead->SrcSerialHI, netmask, gateway, finder->user_data_);
            } else {
                finder->callback_(devinfo->DeviceName, deviceip, devinfo->port, 0, pHead->SrcSerialLO, pHead->SrcSerialHI, netmask, gateway, finder->user_data_);
            }
        }

    }
}