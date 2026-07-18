#pragma once

#include<VzLPRClientSDK.h>
#include "afxwin.h"
// CDlgStreamCloud dialog

class CDlgStreamCloud : public CDialog
{
	DECLARE_DYNAMIC(CDlgStreamCloud)

public:
	CDlgStreamCloud(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgStreamCloud();

// Dialog Data
	enum { IDD = IDD_DLG_STREAM_CLOUD };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	afx_msg void OnBnClickedBtnQuery();
	afx_msg void OnLvnItemchangedListPlate(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedBtnPlay();
	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle(VzLPRClientHandle hLPRClient);

private:
	COleDateTime m_dtStart;
	COleDateTime m_dtStartTime;
	COleDateTime m_dtEnd;
	COleDateTime m_dtEndTime;

	CListCtrl m_lstRecordFile;

	VzLPRClientHandle m_hLPRClient;
	CStatic m_play_wnd;
	int m_select_index;
	long m_play_handle;
public:
	afx_msg void OnClose();
};
