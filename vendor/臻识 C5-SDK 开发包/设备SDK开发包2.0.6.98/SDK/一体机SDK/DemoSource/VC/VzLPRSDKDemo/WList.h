#pragma once
#include "VzLPRClientSDKDefine.h"
#include "DlgOutputAndInBase.h"
#include "Resource.h"

// CWList dialog

class CWList : public CDlgOutputAndInBase
{
	DECLARE_DYNAMIC(CWList)

public:
	CWList();   // standard constructor
	virtual ~CWList();

	virtual void refresh();
	int IDD() { return IDD_DLG_WList;};

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	afx_msg void OnBnClickedBtnFuzzy();
	afx_msg void OnBnClickedRadioAuto2();
	afx_msg void OnBnClickedRadioEnable2();
	afx_msg void OnBnClickedRadioDisable2();
	afx_msg void OnBnClickedBtnWlenable();
	DECLARE_MESSAGE_MAP()

private:
	void SetWindowEnable(BOOL enable);

private:
	int m_nWLEnable;
	int m_nFuzzy;
	int m_nFuzzyLen;
	BOOL m_bFuzzyCC;


};
