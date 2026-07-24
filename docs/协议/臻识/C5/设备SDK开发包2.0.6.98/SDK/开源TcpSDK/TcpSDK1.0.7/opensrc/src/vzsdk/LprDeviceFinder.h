/**************************************************************************
* Copyright ?, 2008-2017, Vision-Zenith Technology Co., Ltd.
* @le:   LprDeviceFinder.h
* @Author: lkb
* @Date:   2017-11-25
* @brief:
*
***************************************************************************/
#ifndef INCLUDE_LPR_DEVICE_FINDER_H
#define INCLUDE_LPR_DEVICE_FINDER_H
#include "MultiRecv.h"

class LprDeviceFinder {
  public:
    LprDeviceFinder();
    ~LprDeviceFinder();

    bool StartFindDevice(VZLPRC_FIND_DEVICE_CALLBACK callback, void *user_data);
    void StopFindDevice();

  private:

    static void OnMultiRecvCallback(
        const char * deviceip,
        const char* data,
        int   size,
        void* user_data
    );

  private:

    vzsdk::MultiRecv MultiRecv_;

    VZLPRC_FIND_DEVICE_CALLBACK callback_;
    void *user_data_;
};


#endif