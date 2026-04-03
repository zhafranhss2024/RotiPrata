import React from "react";
import { MainLayout } from "@/components/layout/MainLayout";
import { AdminLessonWizard } from "@/features/admin/wizard/AdminLessonWizard";

const CreateLessonPage = () => (
  <MainLayout fullScreen>
    <div className="w-full px-3 py-4 md:px-6 md:py-6 xl:px-8 pb-safe">
      <AdminLessonWizard mode="create" />
    </div>
  </MainLayout>
);

export default CreateLessonPage;
