/**************************************************************************
* Copyright ?, 2008-2017, Vision-Zenith Technology Co., Ltd.
* @le:   DeviceFinderManage.h
* @Author: lkb
* @Date:   2017-11-27
* @brief:
*
***************************************************************************/
#ifndef INCLUDE_DEVICE_FINDER_MANAGE_H
#define  INCLUDE_DEVICE_FINDER_MANAGE_H

#include <vector>
#include "LprDeviceFinder.h"
#include "posix_thread.h"

namespace vzsdk {
class DeviceFinderManage {
  public:
    DeviceFinderManage();

    ~DeviceFinderManage();

    bool StartFindDevice(VZLPRC_FIND_DEVICE_CALLBACK callback, void *user_data);

    void StopFindDevice();


  private:

    bool IsExist(const std::string &text);

    static  void __STDCALL OnFindDeviceCallback(const char *pStrDevName,
            const char *pStrIPAddr, WORD usPort1,
            WORD usPort2, unsigned int SL, unsigned int SH,
            char* netmask, char* gateway, void *pUserData);

  private:
    LprDeviceFinder LprDeviceFinder_;

    VZLPRC_FIND_DEVICE_CALLBACK callback_;
    void *user_data_;

    bool   run_flag_;

    Mutex mutex_;

    std::vector<std::string>  ipGroup_;
};
}

#endif