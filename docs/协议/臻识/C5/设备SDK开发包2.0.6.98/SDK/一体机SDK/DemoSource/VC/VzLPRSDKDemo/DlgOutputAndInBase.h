/***************************************
 * \file DlgOutputAndInBase.h
 * \date 2016/01/27
 *
 *
 * \brief 输出配置中的对话框基类
 *
 * TODO: long description
 *
 * \note
 *
 * Copyright@ 2016 vzenith.com
****************************************/
#pragma once

#include "VzLPRClientSDKDefine.h"

class CDlgOutputAndInBase : public CDialog
{
	//DECLARE_DYNAMIC(CDlgOutputAndInBase)

public:
	CDlgOutputAndInBase() : m_hLPRClient(NULL) {}
	virtual ~CDlgOutputAndInBase() {}

	void SetLPRHandle( VzLPRClientHandle hLPRClient )
	{
		m_hLPRClient = hLPRClient;
	}

	virtual int IDD() = 0;			//对话框ID
	virtual void refresh() = 0;		//对话框刷新

protected:
	VzLPRClientHandle m_hLPRClient;	//设备句柄
};