/***************************************
 * \file OutputConfig.h
 * \date 2016/01/27
 *
 *
 * \brief  ‰≥ˆ≈‰÷√∂‘ª∞øÚ
 *
 * TODO: long description
 *
 * \note
 *
 * Copyright@ 2016 vzenith.com
****************************************/
#pragma once

#include "DlgOutputAndInBase.h"
#include "VzLPRClientSDKDefine.h"
#include <map>
#include "Resource.h"

const int c_nCheck = 1;

class COutputConfig : public CDlgOutputAndInBase
{
	DECLARE_DYNAMIC(COutputConfig)

public:
	COutputConfig();   
	virtual ~COutputConfig();
	
	void refresh();
	int IDD() { return IDD_DLG_OutputConfig; };

protected:
	void initData();

	virtual void DoDataExchange(CDataExchange* pDX); 

	DECLARE_MESSAGE_MAP()


public:
	afx_msg void OnBnClickedOk();
	void MoveCtrl(int ID, int left_value);

private:
	VZ_OutputConfigInfo m_oConfigInfo;
public:
	virtual BOOL OnInitDialog();
};
