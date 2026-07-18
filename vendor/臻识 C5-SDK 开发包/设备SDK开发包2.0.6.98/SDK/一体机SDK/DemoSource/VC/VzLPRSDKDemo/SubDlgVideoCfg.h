#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "afxwin.h"

// CSubDlgVideoCfg dialog

class CSubDlgVideoCfg : public CDialog
{
	DECLARE_DYNAMIC(CSubDlgVideoCfg)

public:
	CSubDlgVideoCfg(CWnd* pParent = NULL);   // standard constructor
	virtual ~CSubDlgVideoCfg();

// Dialog Data
	enum { IDD = IDD_DLG_SUB_VIDEO_CFG_1 };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );

private:
	VzLPRClientHandle m_hLPRClient;
public:
	int m_nBoardType;
	CComboBox m_cmbFrameSize;
	CComboBox m_cmbFrameRate;
	CComboBox m_cmbEncodeType;
	CComboBox m_cmbCompressMode;
	CComboBox m_cmbImgQuality;
	virtual BOOL OnInitDialog();
	afx_msg void OnBnClickedOk();
	afx_msg void OnCbnSelchangeCmbEncodeType();
	afx_msg void OnCbnSelchangeCmbCompressMode();
	CEdit m_editRateval;

	void Load( );
	afx_msg void OnCbnSelchangeCmbFrameSize();
	void SetBoardType(int type);
	void Get3730Param();
	void Get8127Param();
};
