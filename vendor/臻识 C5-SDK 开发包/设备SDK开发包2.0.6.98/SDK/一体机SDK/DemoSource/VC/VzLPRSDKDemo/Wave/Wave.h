#ifndef _WAVE_H_
#define _WAVE_H_

#include "stdlib.h"
#include "stdio.h"
#include "string.h"

extern struct WAVE_FORMAT gWaveFmtPcmC2B16R8K;
extern struct WAVE_FORMAT gWaveFmtPcmC1B16R8K;

struct RIFF_HEADER
{
	char szRiffID[4];  // 'R','I','F','F'
	unsigned long dwRiffSize;
	char szRiffFormat[4]; // 'W','A','V','E'
};

//dwAvgBytesPerSec = dwSamplesPerSec
//					*wChannels
//					*wBitsPerSample/8

struct WAVE_FORMAT
{
	unsigned short wFormatTag;
	unsigned short wChannels;
	unsigned long dwSamplesPerSec;
	unsigned long dwAvgBytesPerSec;
	unsigned short wBlockAlign;
	unsigned short wBitsPerSample;
};

struct FMT_BLOCK
{
	char  szFmtID[4]; // 'f','m','t',' '
	unsigned long  dwFmtSize;
	WAVE_FORMAT wavFormat;
};

struct FACT_BLOCK
{
	char  szFactID[4]; // 'f','a','c','t'
	unsigned long  dwFactSize;
};

struct DATA_BLOCK
{
	char szDataID[4]; // 'd','a','t','a'
	unsigned long dwDataSize;
};

/*
typedef struct WAVE_FILE
{
	RIFF_HEADER riffHead;	//12
	FMT_BLOCK fmtBlock;		//24
	char added[2];			//2
	FACT_BLOCK factBlock;	//8
	char factData[4];		//4
	DATA_BLOCK dataBlock;	//8
}
WAVE_FILE;
//*/

typedef struct WaveFile
{
	FILE *pFile;
	int dataSize;
}
WaveFile;

WaveFile *WaveFileOpen(char *fileName, WAVE_FORMAT *pWaveFormat);
WaveFile *WaveFileOpenForRead(char *fileName, WAVE_FORMAT *pWaveFormat = NULL);
void WaveFileWrite(WaveFile *pWaveFile, const void *pData, int size);
int WaveFileRead(WaveFile *pWaveFile, void *pData, int size);
void WaveFileClose(WaveFile *pWaveFile);
void WaveFileCloseForRead(WaveFile *pWaveFile);
#endif