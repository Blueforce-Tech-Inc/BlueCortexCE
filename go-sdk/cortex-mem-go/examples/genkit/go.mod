module github.com/abforce/cortex-ce/cortex-mem-go/examples/genkit

go 1.22

require (
	github.com/abforce/cortex-ce/cortex-mem-go v0.0.0
	github.com/abforce/cortex-ce/cortex-mem-go/genkit v0.0.0-00010101000000-000000000000
)

replace (
	github.com/abforce/cortex-ce/cortex-mem-go => ../..
	github.com/abforce/cortex-ce/cortex-mem-go/eino => ../../eino
	github.com/abforce/cortex-ce/cortex-mem-go/genkit => ../../genkit
	github.com/abforce/cortex-ce/cortex-mem-go/langchaingo => ../../langchaingo
)
