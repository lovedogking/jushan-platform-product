#pragma once
#include "afxwin.h"
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
// CDlgSubVideoCfgR1 dialog

class CDlgSubVideoCfgR1 : public CDialog
{
	DECLARE_DYNAMIC(CDlgSubVideoCfgR1)

public:
	CDlgSubVideoCfgR1(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgSubVideoCfgR1();

// Dialog Data
	enum { IDD = IDD_DLG_SUB_VIDEO_CFG_R1 };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	afx_msg void OnCbnSelchangeCmbStreamType();
	afx_msg void OnBnClickedOk();
	afx_msg void OnCbnSelchangeCmbCompressMode();
	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );
	void Load( );

private:
	void LoadStreamParam( int stream_index );
	void LoadStreamParamRC(int stream_index);
	int SetRCVideoFrame();

private:
	VzLPRClientHandle m_hLPRClient;
	CComboBox m_cmbEncodeType;
	CComboBox m_cmbFrameRate;
	CComboBox m_cmbCompressMode;
	CComboBox m_cmbImgQuality;
	CComboBox m_cmbFrameSize;
	CEdit m_editRateval;
	CComboBox m_cmbStreamType;

	int m_nMinDataRate;
	int m_nMaxDataRate;

	int m_nDeviceType;

	VZ_LPRC_ENCODE_PROP m_encode_prop;
};
