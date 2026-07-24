/***************************************
 * \file OutAndIn.h
 * \date 2016/01/27
 *
 *
 * \brief 输出输出对话框
 *
 * TODO: long description
 *
 * \note
 *
 * Copyright@ 2016 vzenith.com
****************************************/
#pragma once
#include "afxcmn.h"
#include "OutputConfig.h"
#include "TriggerDelay.h"
#include "WList.h"
#include <map>
using namespace std;

class COutAndIn : public CDialog
{
	DECLARE_DYNAMIC(COutAndIn)

public:
	COutAndIn(CWnd* parent = NULL);   // standard constructor
	virtual ~COutAndIn();

	void setHandle(VzLPRClientHandle handle);

// Dialog Data
	enum { IDD = IDD_OutAndIn };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	void initData();
	virtual BOOL OnInitDialog();

	DECLARE_MESSAGE_MAP()

private:
	CTabCtrl m_oTab;

	typedef std::map<int, CDlgOutputAndInBase*> DlgMap;
	DlgMap m_oDlgMap;

	int m_curSel;

	VzLPRClientHandle m_hLPRC;
public:
	afx_msg void OnTcnSelchangeTab2(NMHDR *pNMHDR, LRESULT *pResult);
};
