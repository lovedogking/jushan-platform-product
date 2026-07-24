#pragma once
#include<VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "afxwin.h"

// CDlgMaintain dialog
enum  UpdateStatus
{
	US_INIT,
	US_UPDATE,
	US_RECOVER_ALL,
	US_RECOVER_PART,
};

class CDlgMaintain : public CDialog
{
	DECLARE_DYNAMIC(CDlgMaintain)

public:
	CDlgMaintain(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgMaintain();

// Dialog Data
	enum { IDD = IDD_DLG_MAINTAIN };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedBtnRecovery();
	afx_msg void OnBnClickedBtnBrowse();
	afx_msg void OnBnClickedBtnUpdate();

	void SetLPRHandle( VzLPRClientHandle hLPRClient );
    void SetUpdateStatus(UpdateStatus us);
private:
	VzLPRClientHandle m_hLPRClient;

	CString m_strFilePath;
	UpdateStatus   m_UpdateStatus;
public:
	CEdit m_editPath;
	afx_msg void OnTimer(UINT_PTR nIDEvent);

	int m_nGetStatTimes;
	afx_msg void OnBnClickedBtnPartrecovery();
	afx_msg void OnBnClickedBtnCancelUpdate();
	virtual BOOL OnInitDialog();
	afx_msg void OnPaint();


	void  SetProgressNum(float iProgress);
	virtual void OnOK();
	afx_msg void OnClose();
};
