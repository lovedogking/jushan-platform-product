#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "afxcmn.h"
#include "afxwin.h"

// CDlgSubVideoCfgR2 dialog

class CDlgSubVideoCfgR2 : public CDialog
{
	DECLARE_DYNAMIC(CDlgSubVideoCfgR2)

public:
	CDlgSubVideoCfgR2(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgSubVideoCfgR2();

// Dialog Data
	enum { IDD = IDD_DLG_SUB_VIDEO_CFG_R2 };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	afx_msg void OnHScroll(UINT nSBCode, UINT nPos, CScrollBar* pScrollBar);
	DECLARE_MESSAGE_MAP()

public:
	void Load( );
	virtual BOOL OnInitDialog();

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );

private:
	VzLPRClientHandle m_hLPRClient;
public:
	CSliderCtrl m_sliderBrt;
	CSliderCtrl m_sliderCst;
	CSliderCtrl m_sliderSat;
	CComboBox m_cmbExposureTime;
	CComboBox m_cmbImgPos;
	CSliderCtrl m_sliderGain;
	CString m_strBrt;
	CString m_strCst;
	CString m_strSat;
	CString m_strGain;
	afx_msg void OnCbnSelchangeCmbExposureTime();
	afx_msg void OnCbnSelchangeCmbImgPos();

	VZ_LPRC_R_VIDEO_PARAM m_video_param;
	afx_msg void OnBnClickedOk();
};
