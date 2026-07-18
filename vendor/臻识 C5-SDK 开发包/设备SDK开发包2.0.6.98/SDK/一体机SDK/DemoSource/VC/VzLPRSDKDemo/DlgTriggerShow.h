#pragma once


// DlgTriggerShow dialog
#include "GDIDraw.h"

class _FRAME_BUFFER
{
public:
	_FRAME_BUFFER()
		: m_pBuffer(NULL), m_uSizeBuffer(0), m_uWidth(0), m_uHeight(0)
	{
	}
	~_FRAME_BUFFER()
	{
		if(m_pBuffer)
			free(m_pBuffer);
	}
	
	bool SetFrame(const unsigned char *pSrc, unsigned uWidth, unsigned uHeight, unsigned uPitch)
	{
		if(uWidth != m_uWidth || m_uHeight != uHeight)
		{
			if(m_pBuffer)
				free(m_pBuffer);
			m_uSizeBuffer = uPitch * uHeight;
			m_pBuffer = (unsigned char *)malloc(m_uSizeBuffer);
		}
		if(m_pBuffer == NULL)
			return(false);

		unsigned char *pDst = m_pBuffer;
		for(unsigned i=0; i<uHeight; i++)
		{
			memcpy(pDst, pSrc, uWidth*3);
			pSrc += uPitch;
			pDst += uWidth*3;
		}

		m_uWidth = uWidth;
		m_uHeight = uHeight;

		return(true);
	}

	bool AddPatch(const unsigned char *pPatch, unsigned uWidth, unsigned uHeight, unsigned uPitch,
		unsigned uX, unsigned uY)
	{
		if(uX + uWidth > m_uWidth || uY + uHeight > m_uHeight || uX < 0 || uY < 0)
			return(false);

		if(m_pBuffer == NULL)
			return(false);

		unsigned uDstPitch = m_uWidth * 3;
		unsigned char *pDst = m_pBuffer + uY * uDstPitch + uX*3;
		for(unsigned i=0; i<uHeight; i++)
		{
			memcpy(pDst, pPatch, uPitch);
			pPatch += uPitch;
			pDst += uDstPitch;
		}

		return(true);
	}

public:
	unsigned char *m_pBuffer;
	unsigned m_uSizeBuffer;
	unsigned m_uWidth;
	unsigned m_uHeight;
};

class DlgTriggerShow : public CDialog
{
	DECLARE_DYNAMIC(DlgTriggerShow)

public:
	DlgTriggerShow(CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgTriggerShow();

	void SetFrame(const unsigned char *pFrame, unsigned uWidth, unsigned uHeight, unsigned uPitch);
	unsigned char *GetFrame(unsigned &uWidth, unsigned &uHeight, unsigned &uPitch);

// Dialog Data
	enum { IDD = IDD_DLG_TRIGGER_SHOW };

protected:
	CRITICAL_SECTION m_csFrame;
	GDIDraw m_draw;

	_FRAME_BUFFER m_fBuffer;

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	virtual BOOL OnInitDialog();
	virtual BOOL DestroyWindow();
	afx_msg void OnPaint();
};
