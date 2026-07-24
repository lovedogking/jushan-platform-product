#ifndef CHANGE_DEVICE_NETWORK_H_
#define CHANGE_DEVICE_NETWORK_H_

// return value
#define CHANGE_SUCCEED 0
#define CHANGE_FAILURE 1

// dsn_height, the device serial number hight interge
// dsn_low, the device serial number low interge 
// multicast_addr = "228.5.6.2"
// multicast_port = 24100
// new_ipaddr£¬new_gateway£¬new_ipmark, if you not want change set to 0
int ChangeDeviceNetwork(unsigned long dsn_high, 
  unsigned long dsn_low,
  const char* multicast_addr,
  unsigned short multicast_port,
  unsigned long new_ipaddr,
  unsigned long new_gateway,
  unsigned long new_ipmask);


const char* GetRecommendSubnetMask(const char* ip_addr);

int VerifyIpaddrGatawayCorrect(const char* ip_addr,
  const char* gateway, const char* net_mask);

#endif // CHANGE_DEVICE_NETWORK_H_